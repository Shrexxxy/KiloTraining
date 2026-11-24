using System.Collections.Concurrent;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using AwesomeAssertions;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Npgsql;

namespace CalorieTraining.Server.Tests;

/// <summary>Проверяет контракт отправки карточек в Open Food Facts.</summary>
public sealed class OffSubmissionTests(ProductsFixture fixture) : IClassFixture<ProductsFixture>
{
    private const string Barcode = "4600000000015";
    private const string Submission = "/products/" + Barcode + "/off-submission";

    /// <summary>По умолчанию запись отключена и маршруты требуют ключ.</summary>
    [Fact]
    public async Task DefaultsToDisabledAndRequiresKey()
    {
        await using var off = await LocalOff.Start();
        using var client = Client(off, enabled: null);
        using var status = await client.GetAsync("/off-submissions/status");
        using var submit = await client.PostAsync(Submission, null);
        client.DefaultRequestHeaders.Clear();
        using var unauthorized = await client.PostAsync(Submission, null);

        status.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await status.Content.ReadFromJsonAsync<JsonElement>();
        body.GetProperty("enabled").GetBoolean().Should().BeFalse();
        body.GetProperty("test").GetBoolean().Should().BeTrue();
        submit.StatusCode.Should().Be(HttpStatusCode.ServiceUnavailable);
        unauthorized.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
        off.Reads.Should().Be(0);
        off.Writes.Should().BeEmpty();
    }

    /// <summary>Передаёт только данные карточки и параметры протокола.</summary>
    [Fact]
    public async Task CreatesMissingProduct()
    {
        await using var off = await LocalOff.Start();
        using var client = Client(off);
        await Seed(client);
        using var response = await client.PostAsync(Submission, null);
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        off.Reads.Should().Be(1);
        off.Writes.Should().ContainSingle().Which.Should().BeEquivalentTo(new Dictionary<string, string>
        {
            ["code"] = Barcode, ["user_id"] = "integration", ["password"] = off.Password,
            ["lc"] = "ru", ["lang"] = "ru", ["product_name_ru"] = "Сырок", ["brands"] = "Марка",
            ["nutrition_data_per"] = "100g", ["nutriment_energy-kcal"] = "410",
            ["nutriment_proteins"] = "8", ["nutriment_fat"] = "26", ["nutriment_carbohydrates"] = "34"
        });
    }

    /// <summary>Сохраняет заполненные поля и проверяет основу пищевой ценности.</summary>
    [Theory]
    [InlineData("""{"nutriments":{"energy-kcal_100g":0}}""", 409)]
    [InlineData("""{"brands":"Существующая марка","lang":"fr","nutriments":{"proteins_100g":0}}""", 200)]
    [InlineData("""{"nutrition_data_per":"serving","nutriments":{"proteins":2}}""", 502)]
    public async Task PreservesExistingFields(string product, int expected)
    {
        await using var off = await LocalOff.Start();
        off.Product = product;
        using var client = Client(off);
        await Seed(client);
        using var response = await client.PostAsync(Submission, null);
        ((int)response.StatusCode).Should().Be(expected);
        if (expected != 200)
            off.Writes.Should().BeEmpty();
        else
        {
            var fields = off.Writes.Should().ContainSingle().Which;
            fields.Should().NotContainKeys("brands", "lang", "nutriment_proteins");
            fields.Should().Contain("nutriment_energy-kcal", "410");
        }
    }

    /// <summary>Отклоняет импорт, неизвестный товар и неверную контрольную цифру.</summary>
    [Fact]
    public async Task RejectsInvalidCardsBeforeContactingOff()
    {
        await using var off = await LocalOff.Start();
        using var client = Client(off);
        await Seed(client);
        await using var connection = new NpgsqlConnection(fixture.ConnectionString);
        await connection.OpenAsync();
        await using var change = new NpgsqlCommand("UPDATE products SET source='openfoodfacts' WHERE barcode=$1", connection);
        change.Parameters.Add(new NpgsqlParameter { Value = Barcode });
        await change.ExecuteNonQueryAsync();

        using var imported = await client.PostAsync(Submission, null);
        using var missing = await client.PostAsync("/products/4600000000022/off-submission", null);
        using var invalid = await client.PostAsync("/products/4600000000018/off-submission", null);
        imported.StatusCode.Should().Be(HttpStatusCode.Conflict);
        missing.StatusCode.Should().Be(HttpStatusCode.NotFound);
        invalid.StatusCode.Should().Be(HttpStatusCode.BadRequest);
        off.Reads.Should().Be(0);
    }

    /// <summary>Обрабатывает ошибки записи без повтора и раскрытия пароля.</summary>
    [Theory]
    [InlineData("body", 502)]
    [InlineData("http", 502)]
    [InlineData("limit", 503)]
    [InlineData("timeout", 502)]
    [InlineData("redirect", 502)]
    [InlineData("large", 502)]
    public async Task HandlesFailures(string behavior, int expected)
    {
        await using var off = await LocalOff.Start();
        off.Behavior = behavior;
        using var client = Client(off);
        await Seed(client);

        using var response = await client.PostAsync(Submission, null);
        ((int)response.StatusCode).Should().Be(expected);
        if (behavior == "limit")
            response.Headers.RetryAfter?.Delta.Should().Be(TimeSpan.FromSeconds(30));
        off.Writes.Should().ContainSingle();
        (await response.Content.ReadAsStringAsync()).Should().NotContain(off.Password);
        string.Join("\n", off.Log.Messages).Should().NotContain(off.Password);
    }

    /// <summary>Параллельные запросы приводят к одной записи.</summary>
    [Fact]
    public async Task ConcurrentRequestsWriteOnce()
    {
        await using var off = await LocalOff.Start();
        off.Behavior = "concurrent";
        using var client = Client(off);
        await Seed(client);

        var first = client.PostAsync(Submission, null);
        await off.Writing.Task.WaitAsync(TimeSpan.FromSeconds(5));
        try
        {
            using var second = await client.PostAsync(Submission, null).WaitAsync(TimeSpan.FromSeconds(5));
            second.StatusCode.Should().Be(HttpStatusCode.Conflict);
        }
        finally
        {
            off.FinishWrite.TrySetResult();
        }
        using var response = await first;
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        off.Writes.Should().ContainSingle();
    }

    private HttpClient Client(LocalOff off, bool? enabled = true)
    {
        var settings = new Dictionary<string, string?>
        {
            ["ConnectionStrings:Products"] = fixture.ConnectionString, ["Api:Key"] = "integration-key",
            ["Off:BaseUrl"] = off.Url, ["Off:User"] = "integration",
            ["Off:Password"] = off.Password, ["Off:UserAgent"] = "KiloTraining-Integration/1"
        };
        if (enabled is not null) settings["Off:Enabled"] = enabled.Value.ToString();
        var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder => builder
            .ConfigureAppConfiguration((_, config) => config.AddInMemoryCollection(settings))
            .ConfigureLogging(logging => logging.AddProvider(off.Log)));
        off.Factory = factory;
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Api-Key", "integration-key");
        return client;
    }

    private static async Task Seed(HttpClient client)
    {
        using var response = await client.PutAsJsonAsync("/products/" + Barcode,
            new { name = "Сырок", brand = "Марка", kcal = 410, protein = 8, fat = 26, carbs = 34 });
        response.EnsureSuccessStatusCode();
    }

    private sealed class LocalOff(WebApplication app) : IAsyncDisposable
    {
        public readonly ConcurrentQueue<Dictionary<string, string>> Writes = new();
        public readonly TaskCompletionSource Writing = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public readonly TaskCompletionSource FinishWrite = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public readonly string Password = Guid.NewGuid().ToString("N");
        public readonly CaptureLog Log = new();
        public WebApplicationFactory<Program>? Factory;
        public string? Product;
        public string? Behavior;
        public int Reads;
        public string Url => app.Urls.Single();

        public static async Task<LocalOff> Start()
        {
            var builder = WebApplication.CreateBuilder();
            builder.WebHost.UseUrls("http://127.0.0.1:0");
            builder.Logging.ClearProviders();
            var app = builder.Build();
            var off = new LocalOff(app);
            app.MapGet("/api/v2/product/{code}.json", (string code) =>
            {
                Interlocked.Increment(ref off.Reads);
                code.Should().Be(Barcode);
                return off.Product is { } product
                    ? Results.Text($$"""{"status":1,"product":{{product}}}""", "application/json")
                    : Results.NotFound(new { status = 0 });
            });
            app.MapPost("/cgi/product_jqm2.pl", (Delegate)off.Write);
            await app.StartAsync();
            return off;
        }

        public async ValueTask DisposeAsync()
        {
            if (Factory is not null) await Factory.DisposeAsync();
            await app.DisposeAsync();
        }

        private async Task<IResult> Write(HttpContext context)
        {
            var form = await context.Request.ReadFormAsync();
            Writes.Enqueue(form.ToDictionary(pair => pair.Key, pair => pair.Value.ToString()));
            switch (Behavior)
            {
                case "body": return Results.Json(new { status = 0, status_verbose = Password });
                case "http": return Results.StatusCode(500);
                case "limit":
                    context.Response.Headers.RetryAfter = "30";
                    return Results.StatusCode(429);
                case "timeout":
                    await Task.Delay(Timeout.Infinite, context.RequestAborted);
                    break;
                case "redirect": return Results.Redirect(Url.Replace("127.0.0.1", "localhost"), preserveMethod: true);
                case "large": return Results.Text(new string('x', 1024 * 1024 + 1));
                case "concurrent":
                    Writing.TrySetResult();
                    await FinishWrite.Task.WaitAsync(context.RequestAborted);
                    Product = """{"nutriments":{"energy-kcal_100g":410}}""";
                    break;
            }
            return Results.Json(new { status = 1, status_verbose = "fields saved" });
        }
    }

    private sealed class CaptureLog : ILoggerProvider, ILogger
    {
        public readonly ConcurrentQueue<string> Messages = new();
        public ILogger CreateLogger(string categoryName) => this;
        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
        public bool IsEnabled(LogLevel logLevel) => true;
        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception,
            Func<TState, Exception?, string> formatter) => Messages.Enqueue(formatter(state, exception) + exception);
        public void Dispose() { }
    }
}
