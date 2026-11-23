using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CalorieTraining.Server.Migrations
{
    /// <inheritdoc />
    public partial class AddProductRu : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "ru",
                table: "products",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "ru",
                table: "products");
        }
    }
}
