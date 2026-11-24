using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using AwesomeAssertions;
using Npgsql;

namespace CalorieTraining.Server.Tests;

/// <summary>Проверяет правила многословного поиска через API.</summary>
public sealed class ProductSearchTests(ProductsFixture fixture) : IClassFixture<ProductsFixture>
{
    /// <summary>Ищет все слова по названию и марке, независимо от порядка.</summary>
    [Fact]
    public async Task FindsAllWordsAcrossNameAndBrand()
    {
        using var client = fixture.CreateClient();
        using var created = await client.PutAsJsonAsync("/products/4600010100001",
            new { name = "Творог зернёный", brand = "Творог Простоквашино", kcal = 120 });
        created.StatusCode.Should().Be(HttpStatusCode.Created);

        foreach (var query in new[] { "творог простоквашино", "простоквашино творог", "простоКВАШИНО" })
            (await Search(client, query)).Should().ContainSingle().Which.GetProperty("barcode").GetString()
                .Should().Be("4600010100001");
        (await Search(client, "творог отсутствует")).Should().BeEmpty();

        await using var connection = new NpgsqlConnection(fixture.ConnectionString);
        await connection.OpenAsync();
        await using var key = new NpgsqlCommand(
            "SELECT search_key FROM products WHERE barcode = '4600010100001'", connection);
        (await key.ExecuteScalarAsync()).Should().Be("творог зерненый простоквашино");
    }

    /// <summary>Уравнивает десятичные разделители, пробел перед процентом, ё и кавычки.</summary>
    [Fact]
    public async Task NormalizesDecimalsAndPunctuation()
    {
        using var client = fixture.CreateClient();
        using var created = await client.PutAsJsonAsync("/products/4600010100002",
            new { name = "Молоко «Бурёнка» 3,2%", kcal = 60 });
        created.StatusCode.Should().Be(HttpStatusCode.Created);

        foreach (var query in new[] { "3,2%", "3.2 %", "3.2%", "бурёнка", "буренка", "молоко буренка" })
            (await Search(client, query)).Should().ContainSingle().Which.GetProperty("barcode").GetString()
                .Should().Be("4600010100002");
    }

    /// <summary>Ограничивает поиск коротких слов и применяет приоритет карточек.</summary>
    [Fact]
    public async Task FiltersShortWordsAndRanksMatches()
    {
        await using var connection = new NpgsqlConnection(fixture.ConnectionString);
        await connection.OpenAsync();
        await using var seed = new NpgsqlCommand("""
            INSERT INTO products (id, barcode, name, kcal, source, ru, created_at, updated_at, search_key)
            VALUES
              (gen_random_uuid(), '4600010100011', 'Чай сырный 5', 10, 'phone', false, now(), now(), 'чай сырный 5'),
              (gen_random_uuid(), '4600010100012', 'Чай с сыром 5', 10, 'openfoodfacts', true, now(), now(), 'чай с сыром 5'),
              (gen_random_uuid(), '4600010100013', 'Сыр 5', 10, 'openfoodfacts', false, now(), now(), 'сыр 5'),
              (gen_random_uuid(), '4600010100014', 'Бессырный 5', 10, 'openfoodfacts', false, now(), now(), 'бессырный 5'),
              (gen_random_uuid(), '4600010100015', 'Питьевой сыр 5', 10, 'openfoodfacts', false, now(), now(), 'питьевой сыр 5'),
              (gen_random_uuid(), '4600010100016', 'Сыр 8', 10, 'phone', false, now(), now(), 'сыр 8'),
              (gen_random_uuid(), '4600010100017', 'Морс тестовый', 10, 'openfoodfacts', true, now(), now(), 'морс тестовый'),
              (gen_random_uuid(), '4600010100018', 'Морс другой', 10, 'openfoodfacts', false, now(), now(), 'морс другой')
            """, connection);
        await seed.ExecuteNonQueryAsync();

        using var client = fixture.CreateClient();
        (await Search(client, "сыр 5")).Select(Barcode).Should().Equal(
            "4600010100011", "4600010100012", "4600010100013", "4600010100015", "4600010100014");
        (await Search(client, "мо")).Select(Barcode).Should().Contain("4600010100017").And.NotContain("4600010100018");
        (await Search(client, "% _ \\ сыр 5")).Select(Barcode).Should().Equal(
            "4600010100011", "4600010100012", "4600010100013", "4600010100015", "4600010100014");
    }

    /// <summary>Отклоняет слишком длинные и пустые после нормализации запросы.</summary>
    [Theory]
    [InlineData("один два три четыре пять шесть семь восемь девять")]
    [InlineData("%_\\")]
    public async Task RejectsInvalidSearch(string query)
    {
        using var client = fixture.CreateClient();
        using var response = await client.GetAsync(Url(query));
        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    /// <summary>Проверяет границу длины необработанного запроса.</summary>
    [Fact]
    public async Task RejectsOverHundredCharacters()
    {
        using var client = fixture.CreateClient();
        using var valid = await client.GetAsync(Url(new string('а', 100)));
        valid.StatusCode.Should().Be(HttpStatusCode.OK);
        using var invalid = await client.GetAsync(Url(new string('а', 101)));
        invalid.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    private static string Url(string query) => "/products/search?q=" + Uri.EscapeDataString(query);

    private static string? Barcode(JsonElement product) => product.GetProperty("barcode").GetString();

    private static async Task<JsonElement[]> Search(HttpClient client, string query) =>
        (await client.GetFromJsonAsync<JsonElement>(Url(query))).EnumerateArray().ToArray();
}
