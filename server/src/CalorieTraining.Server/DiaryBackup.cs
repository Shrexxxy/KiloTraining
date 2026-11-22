using System.ComponentModel.DataAnnotations.Schema;

namespace CalorieTraining.Server;

[Table("diary_backups")]
internal sealed class DiaryBackup
{
    /// <summary>Идентификатор копии.</summary>
    [Column("id")]
    public Guid Id { get; init; }

    /// <summary>Время создания в UTC.</summary>
    [Column("created_at")]
    public DateTime CreatedAt { get; init; }

    /// <summary>Размер тела запроса в байтах.</summary>
    [Column("size_bytes")]
    public int SizeBytes { get; init; }

    /// <summary>Версия формата дневника.</summary>
    [Column("format")]
    public required string Format { get; init; }

    /// <summary>Исходный JSON дневника.</summary>
    [Column("content")]
    public required string Content { get; init; }
}
