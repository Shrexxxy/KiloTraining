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

        await using var connection = new NpgsqlConnection(database.GetConnectionString());
        await connection.OpenAsync();
        await using var command = new NpgsqlCommand("SELECT to_regclass('public.products') IS NOT NULL", connection);
        (await command.ExecuteScalarAsync()).Should().Be(migrate);
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
