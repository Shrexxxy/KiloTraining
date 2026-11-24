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

    /// <summary>Создаёт отдельные карточки и обновляет каждую по её ключу.</summary>
    [Fact]
    public async Task KeepsNamesWithAndWithoutPunctuationSeparate()
    {
        using var client = fixture.CreateClient();
        var spicy = await Post(client, "Суп (острый)", 100, HttpStatusCode.Created);
        var plain = await Post(client, "Суп острый", 200, HttpStatusCode.Created);
        plain.Should().NotBe(spicy);
        await AssertSoupCalories(100, 200);

        (await Post(client, "Суп (острый)", 101, HttpStatusCode.OK)).Should().Be(spicy);
        await AssertSoupCalories(101, 200);
        (await Post(client, "Суп острый", 201, HttpStatusCode.OK)).Should().Be(plain);
        await AssertSoupCalories(101, 201);

        async Task AssertSoupCalories(decimal spicyKcal, decimal plainKcal)
        {
            (await Search(client, "суп острый")).ToDictionary(Id, Kcal)
                .Should().Equal(new Dictionary<Guid, decimal> { [spicy] = spicyKcal, [plain] = plainKcal });
        }
    }

    /// <summary>Обновляет нужную карточку среди сохранённых под прежними ключами.</summary>
    [Fact]
    public async Task UpdatesExactExistingKeyWithoutBarcode()
    {
        var original = Guid.NewGuid();
        var other = Guid.NewGuid();
        await using var connection = new NpgsqlConnection(fixture.ConnectionString);
        await connection.OpenAsync();
        await using var seed = new NpgsqlCommand("""
            INSERT INTO products (id, name, kcal, source, ru, created_at, updated_at, search_key)
            VALUES
              ($1, 'Борщ (мой)', 10, 'phone', false, now(), now(), 'борщ (мой)'),
              ($2, 'Борщ мой', 20, 'phone', false, now(), now(), 'борщ мой')
            """, connection);
        seed.Parameters.Add(new NpgsqlParameter { Value = original });
        seed.Parameters.Add(new NpgsqlParameter { Value = other });
        await seed.ExecuteNonQueryAsync();

        using var client = fixture.CreateClient();
        (await Post(client, "Борщ (мой)", 11, HttpStatusCode.OK)).Should().Be(original);
        (await Search(client, "борщ мой")).ToDictionary(Id, Kcal)
            .Should().Equal(new Dictionary<Guid, decimal> { [original] = 11, [other] = 20 });
    }

    /// <summary>Сохраняет разные названия, состоящие только из знаков препинания.</summary>
    [Fact]
    public async Task KeepsPunctuationOnlyNamesSeparate()
    {
        using var client = fixture.CreateClient();
        var exclamations = await Post(client, "!!!", 10, HttpStatusCode.Created);
        var questions = await Post(client, "???", 20, HttpStatusCode.Created);
        questions.Should().NotBe(exclamations);
        (await fixture.Count("search_key", "!!!")).Should().Be(1);
        (await fixture.Count("search_key", "???")).Should().Be(1);
    }

    /// <summary>Находит карточку по словам и вариантам записи процента в ключе со знаками.</summary>
    [Fact]
    public async Task FindsPhoneProductWithPunctuationInKey()
    {
        using var client = fixture.CreateClient();
        var id = await Post(client, "Каша «Овсяная» 3,2 %", 50, HttpStatusCode.Created);
        (await fixture.Count("search_key", "каша «овсяная» 3,2 %")).Should().Be(1);

        (await Search(client, "овсяная каша")).Select(Id).Should().Equal(id);
        var percentMatches = (await Search(client, "3.2%")).Select(Id).ToArray();
        percentMatches.Should().Contain(id);
        foreach (var query in new[] { "3,2%", "3.2 %" })
            (await Search(client, query)).Select(Id).Should().BeEquivalentTo(percentMatches);
    }

    private static async Task<Guid> Post(HttpClient client, string name, int kcal, HttpStatusCode status)
    {
        using var response = await client.PostAsJsonAsync("/products", new { name, kcal });
        response.StatusCode.Should().Be(status, "POST названия {0} с {1} ккал", name, kcal);
        return Id(await response.Content.ReadFromJsonAsync<JsonElement>());
    }

    private static Guid Id(JsonElement product) => product.GetProperty("id").GetGuid();

    private static decimal Kcal(JsonElement product) => product.GetProperty("kcal").GetDecimal();

    private static string Url(string query) => "/products/search?q=" + Uri.EscapeDataString(query);

    private static string? Barcode(JsonElement product) => product.GetProperty("barcode").GetString();

    private static async Task<JsonElement[]> Search(HttpClient client, string query) =>
        (await client.GetFromJsonAsync<JsonElement>(Url(query))).EnumerateArray().ToArray();
}
