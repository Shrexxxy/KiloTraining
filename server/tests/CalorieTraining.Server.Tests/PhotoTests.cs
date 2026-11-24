using System.Net;
using System.Net.Http.Json;
using System.Security.Cryptography;
using AwesomeAssertions;
using Npgsql;

namespace CalorieTraining.Server.Tests;

/// <summary>Проверяет API фото и хранение в PostgreSQL.</summary>
public sealed class PhotoTests(ProductsFixture fixture) : IClassFixture<ProductsFixture>
{
    /// <summary>Проверяет точную передачу JPEG и повторную отправку.</summary>
    [Fact]
    public async Task StoresOriginalJpegOnce()
    {
        using var client = fixture.CreateClient();
        byte[] jpeg = [255, 216, 255, 1];
        var hash = Hash(jpeg);
        (await Put(client, hash, jpeg)).Should().Be(HttpStatusCode.Created);
        using var response = await client.GetAsync($"/photos/{hash}");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        response.Content.Headers.ContentType?.MediaType.Should().Be("image/jpeg");
        (await response.Content.ReadAsByteArrayAsync()).Should().Equal(jpeg);
        (await Put(client, hash, jpeg)).Should().Be(HttpStatusCode.OK);
        (await Count(hash)).Should().Be(1);
    }

    /// <summary>Проверяет формат, совпадение хеша и предел размера.</summary>
    [Fact]
    public async Task ValidatesHashJpegAndSize()
    {
        using var client = fixture.CreateClient();
        byte[] invalid = [1, 2, 3];
        var oversized = new byte[2 * 1024 * 1024 + 1];
        new byte[] { 255, 216, 255 }.CopyTo(oversized, 0);
        var valid = oversized[..^1];
        (await Put(client, "zz", valid)).Should().Be(HttpStatusCode.BadRequest);
        (await Put(client, Hash(invalid), invalid)).Should().Be(HttpStatusCode.BadRequest);
        (await Put(client, Hash(invalid), valid)).Should().Be(HttpStatusCode.BadRequest);
        (await Put(client, Hash(oversized), oversized)).Should().Be(HttpStatusCode.RequestEntityTooLarge);
        (await Count(Hash(invalid))).Should().Be(0);
        (await Count(Hash(oversized))).Should().Be(0);
        (await Put(client, Hash(valid), valid)).Should().Be(HttpStatusCode.Created);
    }

    /// <summary>Проверяет поиск отсутствующих фото и ограничение списка.</summary>
    [Fact]
    public async Task FindsOnlyMissingHashes()
    {
        using var client = fixture.CreateClient();
        byte[] first = [255, 216, 255, 2];
        byte[] second = [255, 216, 255, 3];
        var absent = new string('a', 64);
        (await Put(client, Hash(first), first)).Should().Be(HttpStatusCode.Created);
        (await Put(client, Hash(second), second)).Should().Be(HttpStatusCode.Created);
        using var response = await client.PostAsJsonAsync("/photos/missing", new { hashes = new[] { Hash(first), absent, Hash(second) } });
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        (await response.Content.ReadFromJsonAsync<string[]>()).Should().Equal(absent);
        using var excessive = await client.PostAsJsonAsync("/photos/missing", new { hashes = Enumerable.Repeat(absent, 501) });
        excessive.StatusCode.Should().Be(HttpStatusCode.BadRequest);
        using var missing = await client.GetAsync($"/photos/{absent}");
        missing.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    /// <summary>Проверяет обязательную авторизацию для всех операций.</summary>
    [Fact]
    public async Task RequiresKey()
    {
        using var client = fixture.CreateClient(null);
        byte[] jpeg = [255, 216, 255];
        var hash = Hash(jpeg);
        (await Put(client, hash, jpeg)).Should().Be(HttpStatusCode.Unauthorized);
        using var download = await client.GetAsync($"/photos/{hash}");
        download.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
        using var missing = await client.PostAsJsonAsync("/photos/missing", new { hashes = new[] { hash } });
        missing.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    private static string Hash(byte[] bytes) => Convert.ToHexStringLower(SHA256.HashData(bytes));

    private static async Task<HttpStatusCode> Put(HttpClient client, string hash, byte[] bytes)
    {
        using var content = new ByteArrayContent(bytes);
        content.Headers.ContentType = new("image/jpeg");
        using var response = await client.PutAsync($"/photos/{hash}", content);
        return response.StatusCode;
    }

    private async Task<long> Count(string hash)
    {
        await using var connection = new NpgsqlConnection(fixture.ConnectionString);
        await connection.OpenAsync();
        await using var command = new NpgsqlCommand("SELECT count(*) FROM photos WHERE sha256 = $1", connection);
        command.Parameters.AddWithValue(hash);
        return (long)(await command.ExecuteScalarAsync())!;
    }
}
