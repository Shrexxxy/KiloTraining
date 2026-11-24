using System.Security.Cryptography;
using System.Text;
using CalorieTraining.Server;
using Microsoft.EntityFrameworkCore;
using Npgsql;

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
if (app.Configuration.GetValue<bool>("Database:MigrateOnStartup"))
{
    await using var scope = app.Services.CreateAsyncScope();
    await scope.ServiceProvider.GetRequiredService<ProductsDb>().Database.MigrateAsync();
}
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
{
    var databaseAvailable = await db.Database.CanConnectAsync(cancellationToken);
    long? productCount = null;
    if (databaseAvailable)
    {
        try
        {
            var estimate = await db.Database.SqlQueryRaw<float?>("""
                SELECT reltuples AS "Value"
                FROM pg_catalog.pg_class
                WHERE oid = pg_catalog.to_regclass('products')
                """).SingleOrDefaultAsync(cancellationToken);
            if (estimate is { } tuples)
                productCount = tuples < 10_000
                    ? await db.Products.LongCountAsync(cancellationToken)
                    : (long)tuples;
        }
        catch (PostgresException error) when (error.SqlState == PostgresErrorCodes.UndefinedTable)
        {
            // При отключённых миграциях PostgreSQL может отвечать до создания схемы.
        }
    }

    return Results.Ok(new { status = "ok", databaseAvailable, productCount });
})
    .WithMetadata(new PublicHealth());

var products = app.MapGroup("/products");
products.MapGet("/search", ProductEndpoints.Search);
products.MapGet("/{barcode}", ProductEndpoints.Get);
products.MapPut("/{barcode}", ProductEndpoints.Put);
products.MapPost("", ProductEndpoints.Post);

var backups = app.MapGroup("/backups");
backups.MapPost("", BackupEndpoints.Upload);
backups.MapGet("", BackupEndpoints.List);
backups.MapGet("/{id:guid}", BackupEndpoints.Download);
app.MapPhotoEndpoints();
app.Run();

/// <summary>Точка входа сервера продуктов.</summary>
public partial class Program;

internal sealed class PublicHealth;
