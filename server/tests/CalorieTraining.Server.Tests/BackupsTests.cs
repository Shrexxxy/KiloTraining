using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using AwesomeAssertions;

namespace CalorieTraining.Server.Tests;

/// <summary>Проверяет хранение копий дневника в PostgreSQL.</summary>
public sealed class BackupsTests(ProductsFixture fixture) : IClassFixture<ProductsFixture>
{
    private const string Format = "calorietraining/14";
    private const string Copy = " { \"format\": \"calorietraining/14\", \"note\": \"Гречка\" }\n";

    /// <summary>Проверяет метаданные и точное совпадение тела скачанной копии.</summary>
    [Fact]
    public async Task PreservesOriginalBytes()
    {
        using var client = fixture.CreateClient();
        var previous = await List(client);
        var created = await Upload(client, Copy);
        var id = created.GetProperty("id").GetGuid();
        var list = await List(client);
        list.GetArrayLength().Should().Be(Math.Min(previous.GetArrayLength() + 1, 30));
        list[0].GetProperty("id").GetGuid().Should().Be(id);
        list[0].GetProperty("createdAt").GetDateTimeOffset().Should()
            .Be(created.GetProperty("createdAt").GetDateTimeOffset());
        list[0].GetProperty("sizeBytes").GetInt32().Should().Be(Encoding.UTF8.GetByteCount(Copy));
        list[0].GetProperty("format").GetString().Should().Be(Format);
        list[0].TryGetProperty("content", out _).Should().BeFalse();

        using var downloaded = await client.GetAsync($"/backups/{id}");
        downloaded.StatusCode.Should().Be(HttpStatusCode.OK);
        downloaded.Content.Headers.ContentType?.MediaType.Should().Be("application/json");
        (await downloaded.Content.ReadAsByteArrayAsync()).Should().Equal(Encoding.UTF8.GetBytes(Copy));
    }

    /// <summary>Проверяет удаление самой старой копии после превышения лимита.</summary>
    [Fact]
    public async Task KeepsThirtyNewestBackups()
    {
        using var client = fixture.CreateClient();
        var ids = new List<Guid>();
        for (var index = 0; index < 31; index++)
        {
            var created = await Upload(client, Copy);
            ids.Add(created.GetProperty("id").GetGuid());
        }

        var list = await List(client);
        list.EnumerateArray().Select(item => item.GetProperty("id").GetGuid())
            .Should().Equal(ids.Skip(1).Reverse());
        using var removed = await client.GetAsync($"/backups/{ids[0]}");
        removed.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    /// <summary>Проверяет ограничения запроса, ключ и поиск отсутствующей копии.</summary>
    [Fact]
    public async Task RejectsInvalidRequests()
    {
        using var client = fixture.CreateClient();
        foreach (var invalid in new[] { "not json", "{}", "[]", "{\"format\":\"calorietraining/x\"}" })
        {
            using var response = await Post(client, invalid);
            response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
        }

        using var oversized = await Post(client, new string(' ', 20 * 1024 * 1024) + Copy);
        oversized.StatusCode.Should().Be(HttpStatusCode.RequestEntityTooLarge);
        var unknownPath = $"/backups/{Guid.NewGuid()}";
        using var missing = await client.GetAsync(unknownPath);
        missing.StatusCode.Should().Be(HttpStatusCode.NotFound);

        using var anonymous = fixture.CreateClient(null);
        using var post = await Post(anonymous, Copy);
        using var list = await anonymous.GetAsync("/backups");
        using var download = await anonymous.GetAsync(unknownPath);
        foreach (var response in new[] { post, list, download })
            response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    private static async Task<HttpResponseMessage> Post(HttpClient client, string json)
    {
        using var body = new StringContent(json, Encoding.UTF8, "application/json");
        return await client.PostAsync("/backups", body);
    }

    private static async Task<JsonElement> Upload(HttpClient client, string json)
    {
        using var response = await Post(client, json);
        response.StatusCode.Should().Be(HttpStatusCode.Created);
        return await response.Content.ReadFromJsonAsync<JsonElement>();
    }

    private static async Task<JsonElement> List(HttpClient client)
    {
        using var response = await client.GetAsync("/backups");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        return await response.Content.ReadFromJsonAsync<JsonElement>();
    }
}
