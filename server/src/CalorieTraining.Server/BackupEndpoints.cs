using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using Microsoft.EntityFrameworkCore;

namespace CalorieTraining.Server;

internal static partial class BackupEndpoints
{
    private const int MaxBytes = 20 * 1024 * 1024;
    private const int RetainedCopies = 30;

    internal static async Task<IResult> Upload(HttpRequest request, ProductsDb db, CancellationToken token)
    {
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

        var bytes = body.GetBuffer().AsMemory(0, (int)body.Length);
        string format;
        string content;
        try
        {
            var hasBom = bytes.Span.StartsWith(new byte[] { 0xef, 0xbb, 0xbf });
            using var document = JsonDocument.Parse(hasBom ? bytes[3..] : bytes,
                new JsonDocumentOptions { MaxDepth = int.MaxValue });
            if (document.RootElement.ValueKind != JsonValueKind.Object ||
                !document.RootElement.TryGetProperty("format", out var property) ||
                property.ValueKind != JsonValueKind.String ||
                property.GetString() is not { } value || !BackupFormat().IsMatch(value))
                return Results.BadRequest();

            format = value;
            content = new UTF8Encoding(false, true).GetString(bytes.Span);
        }
        catch (Exception error) when (error is JsonException or DecoderFallbackException or InvalidOperationException)
        {
            return Results.BadRequest();
        }

        await using var transaction = await db.Database.BeginTransactionAsync(token);
        // Сериализуем сохранение и удаление старых копий, включая запросы из разных процессов.
        await db.Database.ExecuteSqlRawAsync("LOCK TABLE diary_backups IN SHARE ROW EXCLUSIVE MODE", token);
        var now = DateTime.UtcNow;
        var backup = new DiaryBackup
        {
            Id = Guid.NewGuid(),
            CreatedAt = now.AddTicks(-(now.Ticks % 10)),
            SizeBytes = bytes.Length,
            Format = format,
            Content = content
        };
        db.DiaryBackups.Add(backup);
        await db.SaveChangesAsync(token);
        await db.Database.ExecuteSqlInterpolatedAsync($"""
            DELETE FROM diary_backups WHERE id IN (
                SELECT id FROM diary_backups ORDER BY created_at DESC, id DESC OFFSET {RetainedCopies}
            )
            """, token);
        await transaction.CommitAsync(token);
        return Results.Created($"/backups/{backup.Id}",
            new BackupMetadata(backup.Id, backup.CreatedAt, backup.SizeBytes, backup.Format));
    }

    internal static async Task<IResult> List(ProductsDb db, CancellationToken token) =>
        Results.Ok(await db.DiaryBackups.OrderByDescending(copy => copy.CreatedAt)
            .ThenByDescending(copy => copy.Id)
            .Select(copy => new BackupMetadata(copy.Id, copy.CreatedAt, copy.SizeBytes, copy.Format))
            .ToListAsync(token));

    internal static async Task<IResult> Download(Guid id, ProductsDb db, CancellationToken token)
    {
        var content = await db.DiaryBackups.Where(copy => copy.Id == id)
            .Select(copy => copy.Content).SingleOrDefaultAsync(token);
        return content is null ? Results.NotFound() : Results.Text(content, "application/json", Encoding.UTF8);
    }

    [GeneratedRegex(@"\Acalorietraining/[0-9]+\z")]
    private static partial Regex BackupFormat();

    private sealed record BackupMetadata(Guid Id, DateTime CreatedAt, int SizeBytes, string Format);
}
