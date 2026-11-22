using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CalorieTraining.Server.Migrations
{
    /// <inheritdoc />
    public partial class InitialProducts : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AlterDatabase()
                .Annotation("Npgsql:PostgresExtension:pg_trgm", ",,");

            migrationBuilder.CreateTable(
                name: "products",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    barcode = table.Column<string>(type: "text", nullable: true),
                    name = table.Column<string>(type: "text", nullable: false),
                    brand = table.Column<string>(type: "text", nullable: true),
                    kcal = table.Column<decimal>(type: "numeric(7,2)", precision: 7, scale: 2, nullable: false),
                    protein = table.Column<decimal>(type: "numeric(7,2)", precision: 7, scale: 2, nullable: true),
                    fat = table.Column<decimal>(type: "numeric(7,2)", precision: 7, scale: 2, nullable: true),
                    carbs = table.Column<decimal>(type: "numeric(7,2)", precision: 7, scale: 2, nullable: true),
                    source = table.Column<string>(type: "text", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    search_key = table.Column<string>(type: "text", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_products", x => x.id);
                    table.CheckConstraint("ck_products_name", "btrim(name) <> ''");
                    table.CheckConstraint("ck_products_nutrition", "kcal >= 0 AND protein >= 0 AND fat >= 0 AND carbs >= 0");
                    table.CheckConstraint("ck_products_source", "source IN ('phone', 'openfoodfacts')");
                });

            migrationBuilder.CreateIndex(
                name: "IX_products_barcode",
                table: "products",
                column: "barcode",
                unique: true,
                filter: "barcode IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "ix_products_phone_without_barcode",
                table: "products",
                column: "search_key",
                unique: true,
                filter: "barcode IS NULL AND source = 'phone'");

            migrationBuilder.CreateIndex(
                name: "ix_products_search_key",
                table: "products",
                column: "search_key")
                .Annotation("Npgsql:IndexMethod", "gin")
                .Annotation("Npgsql:IndexOperators", new[] { "gin_trgm_ops" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "products");
        }
    }
}
