using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace CalorieTraining.Server;

[Table("photos")]
internal sealed class Photo
{
    /// <summary>Хеш содержимого в нижнем регистре.</summary>
    [Key, Column("sha256")]
    public required string Sha256 { get; init; }

    /// <summary>Исходные байты JPEG.</summary>
    [Column("content")]
    public required byte[] Content { get; init; }

    /// <summary>Время сохранения в UTC.</summary>
    [Column("created_at")]
    public DateTime CreatedAt { get; init; }
}
