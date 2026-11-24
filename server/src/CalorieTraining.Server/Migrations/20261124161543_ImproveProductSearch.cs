using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CalorieTraining.Server.Migrations
{
    /// <inheritdoc />
    public partial class ImproveProductSearch : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateIndex(
                name: "ix_products_short_search",
                table: "products",
                column: "id",
                filter: "source = 'phone' OR ru")
                .Annotation("Npgsql:CreatedConcurrently", true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "ix_products_short_search",
                table: "products");
        }
    }
}
