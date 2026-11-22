using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace CalorieTraining.Server;

internal sealed class ProductsDb(DbContextOptions<ProductsDb> options) : DbContext(options)
{
    internal DbSet<Product> Products => Set<Product>();

    /// <inheritdoc/>
    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasPostgresExtension("pg_trgm");
        var product = modelBuilder.Entity<Product>();
        product.ToTable("products", table =>
        {
            table.HasCheckConstraint("ck_products_name", "btrim(name) <> ''");
            table.HasCheckConstraint("ck_products_nutrition", "kcal >= 0 AND protein >= 0 AND fat >= 0 AND carbs >= 0");
            table.HasCheckConstraint("ck_products_source", "source IN ('phone', 'openfoodfacts')");
        });
        product.Property(p => p.Kcal).HasPrecision(7, 2);
        product.Property(p => p.Protein).HasPrecision(7, 2);
        product.Property(p => p.Fat).HasPrecision(7, 2);
        product.Property(p => p.Carbs).HasPrecision(7, 2);
        product.HasIndex(p => p.Barcode).IsUnique().HasFilter("barcode IS NOT NULL");
        product.HasIndex(p => p.SearchKey, "ix_products_phone_without_barcode").IsUnique()
            .HasFilter("barcode IS NULL AND source = 'phone'");
        product.HasIndex(p => p.SearchKey, "ix_products_search_key").HasMethod("gin").HasOperators("gin_trgm_ops");
    }
}

internal sealed class Product
{
    /// <summary>Идентификатор карточки.</summary>
    [Column("id")]
    public Guid Id { get; init; }

    /// <summary>Штрихкод продукта.</summary>
    [Column("barcode")]
    public string? Barcode { get; init; }

    /// <summary>Название продукта.</summary>
    [Column("name")]
    public required string Name { get; init; }

    /// <summary>Марка продукта.</summary>
    [Column("brand")]
    public string? Brand { get; init; }

    /// <summary>Калорийность на 100 г.</summary>
    [Column("kcal")]
    public decimal Kcal { get; init; }

    /// <summary>Белки на 100 г.</summary>
    [Column("protein")]
    public decimal? Protein { get; init; }

    /// <summary>Жиры на 100 г.</summary>
    [Column("fat")]
    public decimal? Fat { get; init; }

    /// <summary>Углеводы на 100 г.</summary>
    [Column("carbs")]
    public decimal? Carbs { get; init; }

    /// <summary>Источник карточки.</summary>
    [Column("source")]
    public required string Source { get; init; }

    /// <summary>Время создания в UTC.</summary>
    [Column("created_at")]
    public DateTime CreatedAt { get; init; }

    /// <summary>Время обновления в UTC.</summary>
    [Column("updated_at")]
    public DateTime UpdatedAt { get; init; }

    /// <summary>Нормализованное название продукта.</summary>
    [Column("search_key")]
    public required string SearchKey { get; init; }
}
