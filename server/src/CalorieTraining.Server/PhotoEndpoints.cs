using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using Microsoft.EntityFrameworkCore;

namespace CalorieTraining.Server;

internal static partial class PhotoEndpoints
{
    private const int MaxBytes = 2 * 1024 * 1024;

    internal static void MapPhotoEndpoints(this WebApplication app)
    {
        var photos = app.MapGroup("/photos");
        photos.MapPut("/{sha256}", Upload);
        photos.MapGet("/{sha256}", Download);
        photos.MapPost("/missing", Missing);
    }

    private static async Task<IResult> Upload(string sha256, HttpRequest request, ProductsDb db, CancellationToken token)
    {
        if (!PhotoHash().IsMatch(sha256) ||
            !MediaTypeHeaderValue.TryParse(request.ContentType, out var type) ||
            !string.Equals(type.MediaType, "image/jpeg", StringComparison.OrdinalIgnoreCase))
            return Results.BadRequest();
        if (request.ContentLength > MaxBytes)
            return Results.StatusCode(StatusCodes.Status413PayloadTooLarge);

        using var body = new MemoryStream();
        var buffer = new byte[64 * 1024];
        while (true)
        {
            var read = await request.Body.ReadAsync(buffer.AsMemory(0,
                Math.Min(buffer.Length, MaxBytes - (int)body.Length + 1)), token);
            if (read == 0)
                break;
            if (body.Length + read > MaxBytes)
                return Results.StatusCode(StatusCodes.Status413PayloadTooLarge);
            body.Write(buffer, 0, read);
        }

        var bytes = body.ToArray();
        if (bytes is not [255, 216, 255, ..] || Convert.ToHexStringLower(SHA256.HashData(bytes)) != sha256)
            return Results.BadRequest();

        var inserted = await db.Database.ExecuteSqlInterpolatedAsync($"""
            INSERT INTO photos (sha256, content, created_at) VALUES ({sha256}, {bytes}, now())
            ON CONFLICT (sha256) DO NOTHING
            """, token);
        return inserted == 0 ? Results.Ok() : Results.Created($"/photos/{sha256}", null);
    }

    private static async Task<IResult> Download(string sha256, ProductsDb db, CancellationToken token)
    {
        if (!PhotoHash().IsMatch(sha256))
            return Results.BadRequest();

        var content = await db.Photos.Where(photo => photo.Sha256 == sha256)
            .Select(photo => photo.Content).SingleOrDefaultAsync(token);
        return content is null ? Results.NotFound() : Results.Bytes(content, "image/jpeg");
    }

    private static async Task<IResult> Missing(MissingRequest request, ProductsDb db, CancellationToken token)
    {
        if (request.Hashes is not { Length: <= 500 } hashes ||
            hashes.Any(hash => hash is null || !PhotoHash().IsMatch(hash)))
            return Results.BadRequest();

        var existing = await db.Photos.Where(photo => hashes.Contains(photo.Sha256))
            .Select(photo => photo.Sha256).ToListAsync(token);
        return Results.Ok(hashes.Except(existing).ToArray());
    }

    [GeneratedRegex(@"\A[0-9a-f]{64}\z")]
    private static partial Regex PhotoHash();

    private sealed record MissingRequest(string[]? Hashes);
}
