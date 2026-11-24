using System.Collections.Concurrent;
using System.Globalization;
using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;

namespace CalorieTraining.Server;

internal sealed class OffSubmission(IConfiguration configuration)
{
    private const string StageHost = "world.openfoodfacts.net";
    private const string ProductionHost = "world.openfoodfacts.org";
    private const int MaxBody = 1024 * 1024;
    private const string Rejected = "Open Food Facts не принял данные";
    private const string AlreadyExists = "Товар уже есть в Open Food Facts";

    private static readonly HttpClient Http = new(new SocketsHttpHandler
    {
        AllowAutoRedirect = false,
        UseCookies = false
    }) { Timeout = Timeout.InfiniteTimeSpan };

    private readonly ConcurrentDictionary<string, byte> active = new();
    private readonly bool enabled = bool.TryParse(configuration["Off:Enabled"], out var configuredEnabled) && configuredEnabled;
    private readonly Uri? url = BaseUrl(configuration["Off:BaseUrl"]);
    private readonly string? user = configuration["Off:User"];
    private readonly string? password = configuration["Off:Password"];
    private readonly string? userAgent = configuration["Off:UserAgent"];

    internal static void Map(WebApplication app)
    {
        app.MapGet("/off-submissions/status", (OffSubmission off) => off.Status());
        app.MapPost("/products/{barcode}/off-submission",
            (string barcode, OffSubmission off, ProductsDb db, HttpContext context) => off.Submit(barcode, db, context));
    }

    private IResult Status() => Results.Ok(new { enabled, test = url is not { Host: ProductionHost, IsDefaultPort: true } });

    private async Task<IResult> Submit(string barcode, ProductsDb db, HttpContext context)
    {
        if (!enabled)
            return Problem(503, "Отправка выключена на сервере");
        if (!IsBarcode(barcode))
            return Problem(400, "Неверный штрихкод");

        var product = await db.Products.AsNoTracking().SingleOrDefaultAsync(p => p.Barcode == barcode, context.RequestAborted);
        if (product is null)
            return Problem(404, "Товар не найден");
        if (product.Source != "phone")
            return Problem(409, "Поделиться можно только своим товаром");
        if (product.Protein is null || product.Fat is null || product.Carbs is null)
            return Problem(400, "Заполните пищевую ценность");
        if (url is null || string.IsNullOrWhiteSpace(user) || string.IsNullOrWhiteSpace(password) ||
            string.IsNullOrWhiteSpace(userAgent))
            return Problem(502, Rejected);
        if (!active.TryAdd(barcode, 0))
            return Problem(409, "Отправка товара уже выполняется");

        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted);
            timeout.CancelAfter(TimeSpan.FromSeconds(15));
            return await Forward(barcode, product, context, timeout.Token);
        }
        catch (Exception error) when (error is HttpRequestException or IOException or JsonException or
                                      FormatException or OperationCanceledException)
        {
            return Problem(502, Rejected);
        }
        finally
        {
            active.TryRemove(barcode, out _);
        }
    }

    private async Task<IResult> Forward(string barcode, Product product, HttpContext context, CancellationToken cancellation)
    {
        var fields = new Dictionary<string, string>
        {
            ["code"] = barcode, ["user_id"] = user!, ["password"] = password!, ["lc"] = "ru"
        };
        using var lookup = Request(HttpMethod.Get,
            $"/api/v2/product/{barcode}.json?fields=product_name,product_name_ru,brands,lang,nutrition_data_per,nutriments");
        using var found = await Http.SendAsync(lookup, HttpCompletionOption.ResponseHeadersRead, cancellation);
        JsonElement existing = default;
        if (found.StatusCode != HttpStatusCode.NotFound)
        {
            if (!found.IsSuccessStatusCode)
                return Failure(found, context);
            using var result = await ReadBody(found, cancellation);
            if (!Success(result.RootElement) || !result.RootElement.TryGetProperty("product", out var card) ||
                card.ValueKind != JsonValueKind.Object)
                return Problem(502, Rejected);
            existing = card.Clone();
        }

        var nutrients = Field(existing, "nutriments");
        if (HasNutrient(nutrients, "energy-kcal"))
            return Problem(409, AlreadyExists);
        var basis = Field(existing, "nutrition_data_per");
        if (Present(basis) && (basis.ValueKind != JsonValueKind.String || basis.GetString() != "100g"))
            return Problem(502, Rejected);
        if (!Present(Field(existing, "product_name_ru")) && !Present(Field(existing, "product_name")))
            fields["product_name_ru"] = product.Name;
        if (!string.IsNullOrWhiteSpace(product.Brand) && !Present(Field(existing, "brands")))
            fields["brands"] = product.Brand;
        if (!Present(Field(existing, "lang")))
            fields["lang"] = "ru";
        if (!Present(basis))
            fields["nutrition_data_per"] = "100g";

        AddNutrient("energy-kcal", product.Kcal);
        AddNutrient("proteins", product.Protein!.Value);
        AddNutrient("fat", product.Fat!.Value);
        AddNutrient("carbohydrates", product.Carbs!.Value);

        using var write = Request(HttpMethod.Post, "/cgi/product_jqm2.pl");
        using var form = new MultipartFormDataContent();
        foreach (var (key, value) in fields)
            form.Add(new StringContent(value), key);
        write.Content = form;

        using var response = await Http.SendAsync(write, HttpCompletionOption.ResponseHeadersRead, cancellation);
        if (!response.IsSuccessStatusCode)
            return Failure(response, context);
        using var body = await ReadBody(response, cancellation);
        return Success(body.RootElement) ? Results.Ok() : Problem(502, Rejected);

        void AddNutrient(string name, decimal value)
        {
            if (!HasNutrient(nutrients, name))
                fields[$"nutriment_{name}"] = value.ToString("0.############################", CultureInfo.InvariantCulture);
        }
    }

    private HttpRequestMessage Request(HttpMethod method, string path)
    {
        var request = new HttpRequestMessage(method, new Uri(url!, path));
        request.Headers.UserAgent.ParseAdd(userAgent!);
        if (url!.Host == StageHost)
        {
            // Публичная авторизация площадки закрывает её от поисковых роботов.
            request.Headers.Authorization = new AuthenticationHeaderValue("Basic", "b2ZmOm9mZg==");
        }
        return request;
    }

    private static Uri? BaseUrl(string? configured)
    {
        var address = string.IsNullOrWhiteSpace(configured) ? $"https://{StageHost}" : configured;
        return Uri.TryCreate(address, UriKind.Absolute, out var parsed) && parsed.UserInfo.Length == 0 &&
            (parsed.Scheme == Uri.UriSchemeHttps || (parsed.IsLoopback && parsed.Scheme == Uri.UriSchemeHttp))
            ? parsed : null;
    }

    private static bool IsBarcode(string code) =>
        code.Length is 8 or 12 or 13 or 14 && code.All(char.IsAsciiDigit) &&
        code.Reverse().Select((digit, index) => (digit - '0') * (index % 2 == 0 ? 1 : 3)).Sum() % 10 == 0;

    private static async Task<JsonDocument> ReadBody(HttpResponseMessage response, CancellationToken cancellation)
    {
        if (response.Content.Headers.ContentLength > MaxBody)
            throw new IOException();
        await using var stream = await response.Content.ReadAsStreamAsync(cancellation);
        using var body = new MemoryStream();
        var buffer = new byte[8192];
        while (true)
        {
            var size = (int)Math.Min(buffer.Length, MaxBody - body.Length + 1);
            var count = await stream.ReadAsync(buffer.AsMemory(0, size), cancellation);
            if (count == 0)
                break;
            if (body.Length + count > MaxBody)
                throw new IOException();
            body.Write(buffer, 0, count);
        }
        return JsonDocument.Parse(body.GetBuffer().AsMemory(0, (int)body.Length));
    }

    private static bool Success(JsonElement root) =>
        Field(root, "status") is { ValueKind: JsonValueKind.Number } value && value.TryGetInt32(out var status) && status == 1;

    private static JsonElement Field(JsonElement item, string name) =>
        item.ValueKind == JsonValueKind.Object && item.TryGetProperty(name, out var value) ? value : default;

    private static bool Present(JsonElement value) => value.ValueKind switch
    {
        JsonValueKind.Undefined or JsonValueKind.Null => false,
        JsonValueKind.String => !string.IsNullOrWhiteSpace(value.GetString()),
        _ => true
    };

    private static bool HasNutrient(JsonElement nutrients, string name) =>
        Present(Field(nutrients, name)) || Present(Field(nutrients, name + "_100g")) ||
        Present(Field(nutrients, name + "_value")) || Present(Field(nutrients, name + "_serving"));

    private static IResult Failure(HttpResponseMessage response, HttpContext context)
    {
        if (response.StatusCode != HttpStatusCode.TooManyRequests)
            return Problem(502, Rejected);
        context.Response.Headers.RetryAfter = response.Headers.RetryAfter?.ToString() ?? "60";
        return Problem(503, Rejected);
    }

    private static IResult Problem(int code, string title) => Results.Problem(statusCode: code, title: title);
}
