using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using AwesomeAssertions;
using Npgsql;

namespace CalorieTraining.Server.Tests;

/// <summary>Проверяет импорт мировой выгрузки и приоритет результатов поиска.</summary>
public sealed class WorldOffImportTests(ProductsFixture fixture) : IClassFixture<ProductsFixture>
{
    private const string German = "4000417025005";
    private const string Header = "code\tproduct_name\tbrands\tcountries_tags\tenergy-kcal_100g\tproteins_100g\tfat_100g\tcarbohydrates_100g\tlast_modified_t";

    /// <summary>Возвращает импортированный иностранный товар по штрихкоду.</summary>
    [Fact]
    public async Task GetsForeignProduct()
    {
        await Import(Row(German, "Шоколад Milka", "en:germany"));
        using var client = fixture.CreateClient();
        using var response = await client.GetAsync($"/products/{German}");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var product = await response.Content.ReadFromJsonAsync<JsonElement>();
        product.GetProperty("kcal").GetDecimal().Should().Be(540);
    }

    /// <summary>Определяет российские товары по метке или префиксу EAN-13.</summary>
    [Fact]
    public async Task SetsRussianFlag()
    {
        await Import(
            Row("4012345678901", "С меткой России", "en:germany,en:russia"),
            Row("4601234567893", "С российским префиксом", "en:germany"),
            Row(German, "Шоколад Milka", "en:germany"),
            Row("460123456789", "Из США", "en:united-states"));

        (await RussianFlag("4012345678901")).Should().Be(true);
        (await RussianFlag("4601234567893")).Should().Be(true);
        (await RussianFlag(German)).Should().Be(false);
        (await RussianFlag("460123456789")).Should().Be(false);
    }

    /// <summary>В режиме старого импорта принимает только товары с российской меткой.</summary>
    [Fact]
    public async Task ImportsOnlyTaggedRussiaWhenRequested()
    {
        const string tagged = "4012345678902";
        const string prefixOnly = "4601234567894";
        const string foreign = "4000417025007";
        var output = Console.Out;
        using var captured = new StringWriter();
        try
        {
            Console.SetOut(captured);
            await WithFile(file => OffImporter.RunCommandAsync(fixture.ConnectionString, ["--only-russia", "--file", file]),
                Row(tagged, "С меткой", "en:russia"),
                Row(prefixOnly, "С префиксом", "en:germany"),
                Row(foreign, "Из Германии", "en:germany"));
        }
        finally
        {
            Console.SetOut(output);
        }

        captured.ToString().Should().Contain("страна: 2");
        (await fixture.Count("barcode", tagged)).Should().Be(1);
        (await fixture.Count("barcode", prefixOnly)).Should().Be(0);
        (await fixture.Count("barcode", foreign)).Should().Be(0);
    }

    /// <summary>Обновляет признак при изменении меток и сохраняет карточку телефона.</summary>
    [Fact]
    public async Task RefreshesOnlyRussianFlagAndKeepsPhoneProduct()
    {
        const string imported = "4000417025008";
        const string phone = "4000417025009";
        using var client = fixture.CreateClient();
        using var created = await client.PutAsJsonAsync($"/products/{phone}", new { name = "Моя карточка", kcal = 90 });
        created.StatusCode.Should().Be(HttpStatusCode.Created);
        var original = await ProductSnapshot(phone);
        await Import(Row(imported, "Какао", "en:germany"), Row(phone, "Импортная карточка", "en:germany"));
        var before = await ProductSnapshot(imported);
        (await RussianFlag(imported)).Should().Be(false);

        var result = await Import(
            Row(imported, "Какао", "en:germany,en:russia"), Row(phone, "Импортная карточка", "en:germany,en:russia"));

        result.Updated.Should().Be(1);
        result.Phone.Should().Be(1);
        result.Added.Should().Be(0);
        (await RussianFlag(imported)).Should().Be(true);
        (await fixture.Count("barcode", imported)).Should().Be(1);
        (await fixture.Count("barcode", phone)).Should().Be(1);
        (await ProductSnapshot(imported, excludeImportChanges: true)).Should().Be(ImportedFields(before));
        (await ProductSnapshot(phone)).Should().Be(original);
    }

    /// <summary>Ставит карточку телефона и российский товар выше совпадений по похожести.</summary>
    [Fact]
    public async Task RanksPhoneThenRussianThenForeign()
    {
        using var client = fixture.CreateClient();
        using var created = await client.PostAsJsonAsync("/products", new { name = "Шоколад домашний", kcal = 500 });
        created.StatusCode.Should().Be(HttpStatusCode.Created);
        await Import(
            Row("4012345678903", "Шоколад Алёнка", "en:russia"),
            Row(German, "Шоколад Milka", "en:germany"));

        var response = await client.GetFromJsonAsync<JsonElement>("/products/search?q=шоколад");
        response.EnumerateArray().Select(product => product.GetProperty("name").GetString())
            .Should().Equal("Шоколад домашний", "Шоколад Алёнка", "Шоколад Milka");

        await Import(
            Row("4012345678910", "Мармелад ABC", "en:germany"),
            Row("4012345678911", "Мармелад XYZ", "en:russia"));

        var tied = await client.GetFromJsonAsync<JsonElement>("/products/search?q=мармелад");
        tied.EnumerateArray().Select(product => product.GetProperty("name").GetString())
            .Should().Equal("Мармелад XYZ", "Мармелад ABC");
    }

    private static string Row(string barcode, string name, string countries) =>
        $"{barcode}\t{name}\t\t{countries}\t540\t\t\t\t1700000000";

    private async Task<OffImportResult> Import(params string[] rows)
    {
        OffImportResult? result = null;
        await WithFile(async file => result = await OffImporter.RunAsync(
            fixture.ConnectionString, file, null, CancellationToken.None), rows);
        return result!;
    }

    private static async Task WithFile(Func<string, Task> action, params string[] rows)
    {
        var file = Path.GetTempFileName();
        try
        {
            await File.WriteAllLinesAsync(file, [Header, .. rows]);
            await action(file);
        }
        finally
        {
            File.Delete(file);
        }
    }

    private async Task<bool?> RussianFlag(string barcode)
    {
        using var product = JsonDocument.Parse(await ProductSnapshot(barcode));
        return product.RootElement.TryGetProperty("ru", out var value) ? value.GetBoolean() : null;
    }

    private async Task<string> ProductSnapshot(string barcode, bool excludeImportChanges = false)
    {
        await using var connection = new NpgsqlConnection(fixture.ConnectionString);
        await connection.OpenAsync();
        await using var command = new NpgsqlCommand(
            "SELECT to_jsonb(p)::text FROM products AS p WHERE barcode = $1", connection);
        command.Parameters.Add(new NpgsqlParameter { Value = barcode });
        var snapshot = (string?)await command.ExecuteScalarAsync();
        snapshot.Should().NotBeNull();
        return excludeImportChanges ? ImportedFields(snapshot!) : snapshot!;
    }

    private static string ImportedFields(string snapshot)
    {
        using var product = JsonDocument.Parse(snapshot);
        return JsonSerializer.Serialize(product.RootElement.EnumerateObject()
            .Where(property => property.Name is not ("ru" or "updated_at"))
            .ToDictionary(property => property.Name, property => property.Value));
    }
}
