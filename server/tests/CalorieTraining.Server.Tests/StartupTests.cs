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

/// <summary>Проверяет запуск API и состояние базы на отдельной PostgreSQL.</summary>
public sealed class StartupTests
{
    /// <summary>Проверяет наличие таблицы после запуска и доступность PostgreSQL.</summary>
    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task AppliesMigrationsOnlyWhenEnabled(bool migrate)
    {
        await using var database = new PostgreSqlBuilder("postgres:17.7").Build();
        await database.StartAsync();
        using var factory = CreateFactory(database.GetConnectionString(), migrate);
        using var client = factory.CreateClient();

        var health = await client.GetFromJsonAsync<JsonElement>("/health");
        health.GetProperty("databaseAvailable").GetBoolean().Should().BeTrue();
        if (!migrate)
            health.GetProperty("productCount").ValueKind.Should().Be(JsonValueKind.Null);

        await using var connection = new NpgsqlConnection(database.GetConnectionString());
        await connection.OpenAsync();
        await using var command = new NpgsqlCommand("SELECT to_regclass('public.products') IS NOT NULL", connection);
        (await command.ExecuteScalarAsync()).Should().Be(migrate);
    }

    /// <summary>Проверяет выбор точного счёта или оценки после изменения числа карточек.</summary>
    [Theory]
    [InlineData(20_000, 20_000)]
    [InlineData(100, 105)]
    public async Task HealthUsesEstimateOnlyForLargeTables(int analyzedCount, long expectedCount)
    {
        await using var database = new PostgreSqlBuilder("postgres:17.7").Build();
        await database.StartAsync();
        using var factory = CreateFactory(database.GetConnectionString(), true);
        using var client = factory.CreateClient();
        await using var scope = factory.Services.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<ProductsDb>();

        await InsertProducts(db, 1, analyzedCount);
        await db.Database.ExecuteSqlRawAsync("ANALYZE products");
        await InsertProducts(db, analyzedCount + 1, analyzedCount + 5);

        var health = await client.GetFromJsonAsync<JsonElement>("/health");
        health.GetProperty("productCount").GetInt64().Should().Be(expectedCount);
    }

    /// <summary>Проверяет ответ без доступной PostgreSQL.</summary>
    [Fact]
    public async Task HealthRespondsWhenDatabaseIsUnavailable()
    {
        await using var database = new PostgreSqlBuilder("postgres:17.7").Build();
        await database.StartAsync();
        using var factory = CreateFactory(database.GetConnectionString(), false);
        using var client = factory.CreateClient();
        await database.StopAsync();

        using var response = await client.GetAsync("/health");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var health = await response.Content.ReadFromJsonAsync<JsonElement>();
        health.GetProperty("databaseAvailable").GetBoolean().Should().BeFalse();
        health.GetProperty("productCount").ValueKind.Should().Be(JsonValueKind.Null);
    }

    /// <summary>Проверяет счётчик до и после появления двух карточек.</summary>
    [Fact]
    public async Task HealthCountsProducts()
    {
        await using var database = new PostgreSqlBuilder("postgres:17.7").Build();
        await database.StartAsync();
        using var factory = CreateFactory(database.GetConnectionString(), false);
        using var client = factory.CreateClient();
        await using var scope = factory.Services.CreateAsyncScope();
        var db = scope.ServiceProvider.GetRequiredService<ProductsDb>();
        await db.Database.MigrateAsync();

        var before = await client.GetFromJsonAsync<JsonElement>("/health");
        before.TryGetProperty("productCount", out var emptyCount).Should().BeTrue();
        emptyCount.GetInt64().Should().Be(0);

        await db.Database.ExecuteSqlRawAsync("""
            INSERT INTO products (id, name, kcal, source, created_at, updated_at, search_key)
            VALUES (gen_random_uuid(), 'Хлеб', 200, 'phone', now(), now(), 'хлеб'),
                   (gen_random_uuid(), 'Молоко', 50, 'phone', now(), now(), 'молоко')
            """);

        var after = await client.GetFromJsonAsync<JsonElement>("/health");
        after.GetProperty("productCount").GetInt64().Should().Be(2);
    }

    /// <summary>Проверяет отказ в доступе при отсутствии настроенного ключа.</summary>
    [Fact]
    public async Task RejectsProductRequestWhenApiKeyIsMissing()
    {
        using var factory = CreateFactory(null, false);
        using var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Api-Key", "integration-test-key");

        using var response = await client.GetAsync("/products/search?q=хлеб");
        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    private static Task<int> InsertProducts(ProductsDb db, int first, int last) =>
        db.Database.ExecuteSqlInterpolatedAsync($"""
            INSERT INTO products (id, name, kcal, source, created_at, updated_at, search_key)
            SELECT gen_random_uuid(), 'Товар ' || n, 100, 'phone', now(), now(), 'товар ' || n
            FROM generate_series({first}, {last}) AS n
            """);

    private static WebApplicationFactory<Program> CreateFactory(string? connectionString, bool migrate) =>
        new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
            builder.ConfigureAppConfiguration((_, configuration) => configuration.AddInMemoryCollection(
                new Dictionary<string, string?>
                {
                    ["ConnectionStrings:Products"] = connectionString,
                    ["Database:MigrateOnStartup"] = migrate.ToString(),
                    ["Api:Key"] = null
                })));
}
