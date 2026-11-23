using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using AwesomeAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Npgsql;
using Testcontainers.PostgreSql;

namespace CalorieTraining.Server.Tests;

/// <summary>Проверяет API продуктов на PostgreSQL.</summary>
public sealed class ProductsTests(ProductsFixture fixture) : IClassFixture<ProductsFixture>
{
    private const string Barcode = "4600000000017";
    private const string GlazedCurd = "Сырок глазированный";

    /// <summary>Создаёт карточку, читает её и обновляет по тому же штрихкоду.</summary>
    [Fact]
    public async Task CreatesReadsAndUpdatesByBarcode()
    {
        using var client = fixture.CreateClient();
        using var created = await client.PutAsJsonAsync($"/products/{Barcode}", Food(GlazedCurd, 410));
        created.StatusCode.Should().Be(HttpStatusCode.Created);
        var product = await Read(await client.GetAsync($"/products/{Barcode}"));
        var id = product.GetProperty("id").GetGuid();
        product.GetProperty("barcode").GetString().Should().Be(Barcode);
        product.GetProperty("name").GetString().Should().Be(GlazedCurd);
        product.GetProperty("kcal").GetDecimal().Should().Be(410);
        product.GetProperty("protein").GetDecimal().Should().Be(8);
        product.GetProperty("fat").GetDecimal().Should().Be(26);
        product.GetProperty("carbs").GetDecimal().Should().Be(34);
        product.GetProperty("source").GetString().Should().Be("phone");

        using var updated = await client.PutAsJsonAsync($"/products/{Barcode}", Food(GlazedCurd, 420));
        updated.StatusCode.Should().Be(HttpStatusCode.OK);
        var saved = await Read(await client.GetAsync($"/products/{Barcode}"));
        saved.GetProperty("id").GetGuid().Should().Be(id);
        saved.GetProperty("kcal").GetDecimal().Should().Be(420);
        (await fixture.Count("barcode", Barcode)).Should().Be(1);
    }

    /// <summary>Возвращает 404 для неизвестного штрихкода.</summary>
    [Fact]
    public async Task ReturnsNotFound()
    {
        using var client = fixture.CreateClient();
        using var response = await client.GetAsync("/products/4600000000024");
        response.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    /// <summary>Ищет по части названия, независимо от регистра и ё.</summary>
    [Theory]
    [InlineData("Сырок глазированный", "СЫРОК", "4600000000031")]
    [InlineData("Сырок глазированный", "сырок глазир", "4600000000048")]
    [InlineData("Творог зернёный", "зерненый", "4600000000055")]
    public async Task SearchesNormalizedSubstring(string name, string query, string barcode)
    {
        using var client = fixture.CreateClient();
        using var created = await client.PutAsJsonAsync($"/products/{barcode}", Food(name, 410));
        created.StatusCode.Should().Be(HttpStatusCode.Created);
        var results = await Read(await client.GetAsync($"/products/search?q={Uri.EscapeDataString(query)}"));
        results.EnumerateArray().Should().Contain(product => product.GetProperty("barcode").GetString() == barcode);
    }

    /// <summary>Отклоняет запрос из одного нормализованного символа.</summary>
    [Fact]
    public async Task RejectsShortSearch()
    {
        using var client = fixture.CreateClient();
        using var response = await client.GetAsync("/products/search?q=%D1%91");
        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    /// <summary>Обновляет карточку без штрихкода по нормализованному названию.</summary>
    [Fact]
    public async Task KeepsOneProductWithoutBarcode()
    {
        using var client = fixture.CreateClient();
        using var created = await client.PostAsJsonAsync("/products", Food("Гречка варёная", 100));
        created.StatusCode.Should().Be(HttpStatusCode.Created);
        var original = await Read(created);
        original.GetProperty("barcode").ValueKind.Should().Be(JsonValueKind.Null);
        using var updated = await client.PostAsJsonAsync("/products", Food("ГРЕЧКА  вареная", 110));
        updated.StatusCode.Should().Be(HttpStatusCode.OK);
        var saved = await Read(updated);
        saved.GetProperty("id").GetGuid().Should().Be(original.GetProperty("id").GetGuid());
        saved.GetProperty("kcal").GetDecimal().Should().Be(110);
        (await fixture.Count("search_key", "гречка вареная")).Should().Be(1);
    }

    /// <summary>Требует ключ для карточек и открывает проверку состояния.</summary>
    [Fact]
    public async Task ChecksApiKeyAndExposesHealth()
    {
        using var client = fixture.CreateClient(null);
        using var missing = await client.GetAsync($"/products/{Barcode}");
        missing.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
        client.DefaultRequestHeaders.Add("X-Api-Key", "wrong-key");
        using var wrong = await client.GetAsync($"/products/{Barcode}");
        wrong.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
        client.DefaultRequestHeaders.Remove("X-Api-Key");
        var health = await Read(await client.GetAsync("/health"));
        health.GetProperty("status").GetString().Should().Be("ok");
        health.GetProperty("databaseAvailable").GetBoolean().Should().BeTrue();
    }

    /// <summary>Отклоняет каждый недопустимый ввод отдельно.</summary>
    [Theory]
    [InlineData("12345", "Кефир", 40, "barcode")]
    [InlineData(Barcode, " ", 40, "name")]
    [InlineData(Barcode, "Кефир", -1, "kcal")]
    public async Task ExplainsInvalidInput(string barcode, string name, int kcal, string field)
    {
        using var client = fixture.CreateClient();
        using var response = await client.PutAsJsonAsync($"/products/{barcode}", Food(name, kcal));
        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        body.GetProperty("errors").GetProperty(field)[0].GetString().Should().NotBeNullOrWhiteSpace();
    }

    private static object Food(string name, int kcal) => new { name, kcal, protein = 8, fat = 26, carbs = 34 };

    private static async Task<JsonElement> Read(HttpResponseMessage response)
    {
        using (response)
        {
            response.EnsureSuccessStatusCode();
            return await response.Content.ReadFromJsonAsync<JsonElement>();
        }
    }
}

/// <summary>Поднимает отдельную базу и хост API для тестов.</summary>
public sealed class ProductsFixture : IAsyncLifetime
{
    private const string Key = "integration-test-key";
    private readonly PostgreSqlContainer database = new PostgreSqlBuilder("postgres:17.7").Build();
    private WebApplicationFactory<Program> factory = null!;

    /// <summary>Запускает PostgreSQL и настраивает хост.</summary>
    public async Task InitializeAsync()
    {
        await database.StartAsync();
        factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
            builder.ConfigureAppConfiguration((_, configuration) => configuration.AddInMemoryCollection(
                new Dictionary<string, string?>
                {
                    ["ConnectionStrings:Products"] = database.GetConnectionString(),
                    ["Api:Key"] = Key
                })));
        await using var scope = factory.Services.CreateAsyncScope();
        await scope.ServiceProvider.GetRequiredService<ProductsDb>().Database.MigrateAsync();
    }

    /// <summary>Останавливает хост и контейнер.</summary>
    public async Task DisposeAsync()
    {
        if (factory is not null)
            await factory.DisposeAsync();
        await database.DisposeAsync();
    }

    internal string ConnectionString => database.GetConnectionString();

    /// <summary>Создаёт клиент с указанным ключом.</summary>
    public HttpClient CreateClient(string? key = Key)
    {
        var client = factory.CreateClient();
        if (key is not null)
            client.DefaultRequestHeaders.Add("X-Api-Key", key);
        return client;
    }

    /// <summary>Подсчитывает карточки с заданным значением.</summary>
    public async Task<long> Count(string column, string value)
    {
        var sql = column switch
        {
            "barcode" => "SELECT count(*) FROM products WHERE barcode = $1",
            "search_key" => "SELECT count(*) FROM products WHERE search_key = $1 AND barcode IS NULL",
            _ => throw new ArgumentOutOfRangeException(nameof(column))
        };
        await using var connection = new NpgsqlConnection(database.GetConnectionString());
        await connection.OpenAsync();
        await using var command = new NpgsqlCommand(sql, connection);
        command.Parameters.Add(new NpgsqlParameter { Value = value });
        return (long)(await command.ExecuteScalarAsync())!;
    }
}
