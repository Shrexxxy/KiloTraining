using System.Security.Cryptography;
using System.Text;
using CalorieTraining.Server;
using Microsoft.EntityFrameworkCore;

if (args is ["import-off", .. var importArgs])
{
    var configuration = WebApplication.CreateBuilder().Configuration;
    try
    {
        await OffImporter.RunCommandAsync(configuration.GetConnectionString("Products"), importArgs);
    }
    catch (Exception error) when (error is ArgumentException or IOException or HttpRequestException
                                  or Npgsql.NpgsqlException or FormatException)
    {
        Console.Error.WriteLine($"Ошибка импорта: {error.Message}");
        Environment.ExitCode = 1;
    }

    return;
}

var builder = WebApplication.CreateBuilder(args);
builder.Configuration["Urls"] ??= "http://0.0.0.0:5080";
builder.Services.AddProblemDetails();
builder.Services.AddDbContext<ProductsDb>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("Products")));

var app = builder.Build();
app.UseExceptionHandler();
app.UseStatusCodePages();
app.Use(async (context, next) =>
{
    var key = app.Configuration["Api:Key"];
    if (context.GetEndpoint()?.Metadata.GetMetadata<PublicHealth>() is not null ||
        (!string.IsNullOrWhiteSpace(key) &&
         context.Request.Headers.TryGetValue("X-Api-Key", out var supplied) &&
         supplied.Count == 1 &&
         CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(key), Encoding.UTF8.GetBytes(supplied[0]!))))
    {
        await next(context);
        return;
    }

    await Results.Problem(statusCode: StatusCodes.Status401Unauthorized, title: "Требуется корректный ключ API.")
        .ExecuteAsync(context);
});

app.MapGet("/health", async (ProductsDb db, CancellationToken cancellationToken) =>
    Results.Ok(new { status = "ok", databaseAvailable = await db.Database.CanConnectAsync(cancellationToken) }))
    .WithMetadata(new PublicHealth());

var products = app.MapGroup("/products");
products.MapGet("/search", ProductEndpoints.Search);
products.MapGet("/{barcode}", ProductEndpoints.Get);
products.MapPut("/{barcode}", ProductEndpoints.Put);
products.MapPost("", ProductEndpoints.Post);
app.Run();

/// <summary>Точка входа сервера продуктов.</summary>
public partial class Program;

internal sealed class PublicHealth;
