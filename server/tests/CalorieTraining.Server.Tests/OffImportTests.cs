using System.IO.Compression;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using AwesomeAssertions;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Testcontainers.PostgreSql;

namespace CalorieTraining.Server.Tests;

/// <summary>Проверяет импорт выгрузки Open Food Facts в PostgreSQL и поиск через API.</summary>
public sealed class OffImportTests
{
    /// <summary>Проверяет фильтрацию, защиту карточки телефона, поиск и повторный импорт.</summary>
    [Fact]
    public async Task ImportsAndRefreshesRussianProducts()
    {
        const string curd = "4600000000017";
        const string phone = "4600000000048";
        await using var database = new PostgreSqlBuilder("postgres:17.7").Build();
        await database.StartAsync();
        var connection = database.GetConnectionString();
        using var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
            builder.ConfigureAppConfiguration((_, configuration) => configuration.AddInMemoryCollection(
                new Dictionary<string, string?>
                {
                    ["ConnectionStrings:Products"] = connection,
                    ["Api:Key"] = "off-test-key"
                })));
        await using var scope = factory.Services.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<ProductsDb>();
        await db.Database.MigrateAsync();
        using var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Api-Key", "off-test-key");
        using var created = await client.PutAsJsonAsync($"/products/{phone}", new { name = "Моя карточка", kcal = 90 });
        created.StatusCode.Should().Be(HttpStatusCode.Created);
        var original = await db.Products.AsNoTracking().SingleAsync(p => p.Barcode == phone);

        using var fixture = typeof(OffImportTests).Assembly.GetManifestResourceStream(
            "CalorieTraining.Server.Tests.Fixtures.off.csv")!;
        using var reader = new StreamReader(fixture);
        var input = await reader.ReadToEndAsync();
        var file = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".csv.gz");
        try
        {
            await using (var archive = new GZipStream(File.Create(file), CompressionMode.Compress))
            await using (var writer = new StreamWriter(archive))
                await writer.WriteAsync(input);

            var first = await OffImporter.RunAsync(connection, file, null, CancellationToken.None, onlyRussia: true);
            first.Read.Should().Be(8);
            first.Eligible.Should().Be(4);
            first.Added.Should().Be(3);
            first.Phone.Should().Be(1);
            first.Rejected.Should().BeEquivalentTo(new Dictionary<string, long>
            {
                ["страна"] = 1, ["штрихкод"] = 1, ["калорийность"] = 1, ["формат"] = 1
            });
            var products = await db.Products.AsNoTracking().ToListAsync();
            products.Select(p => p.Barcode).Should().BeEquivalentTo(
                new[] { curd, phone, "4600000000062", "4600000000079" });
            products.Single(p => p.Barcode == "4600000000062").Name.Should().Be("Хлеб\tржаной");
            products.Single(p => p.Barcode == phone).Should().BeEquivalentTo(original);
            var imported = products.Single(p => p.Barcode == curd);
            imported.Protein.Should().BeNull();
            var results = await client.GetFromJsonAsync<JsonElement>("/products/search?q=зерненый");
            results.EnumerateArray().Should().ContainSingle().Which.GetProperty("source").GetString()
                .Should().Be("openfoodfacts");

            var plain = Path.ChangeExtension(file, null);
            try
            {
                await File.WriteAllTextAsync(plain, input.Replace("\t120\t", "\t125\t", StringComparison.Ordinal));
                var second = await OffImporter.RunAsync(connection, plain, null, CancellationToken.None, onlyRussia: true);
                second.Updated.Should().Be(1);
                second.Added.Should().Be(0);
                second.Unchanged.Should().Be(2);
                second.Phone.Should().Be(1);
                var refreshed = await db.Products.AsNoTracking().SingleAsync(p => p.Barcode == curd);
                refreshed.Id.Should().Be(imported.Id);
                refreshed.Kcal.Should().Be(125);
                refreshed.UpdatedAt.Should().BeAfter(imported.UpdatedAt);
            }
            finally
            {
                File.Delete(plain);
            }
        }
        finally
        {
            File.Delete(file);
        }
    }
}
