using Microsoft.EntityFrameworkCore;

namespace CalorieTraining.Server;

internal sealed record ProductInput(string? Name, string? Brand, decimal? Kcal, decimal? Protein, decimal? Fat, decimal? Carbs);

internal sealed record ProductResponse(
    Guid Id, string? Barcode, string Name, string? Brand, decimal Kcal, decimal? Protein, decimal? Fat, decimal? Carbs,
    string Source, DateTime CreatedAt, DateTime UpdatedAt)
{
    internal static ProductResponse From(Product product) => new(
        product.Id, product.Barcode, product.Name, product.Brand, product.Kcal, product.Protein, product.Fat, product.Carbs,
        product.Source, product.CreatedAt, product.UpdatedAt);
}

internal static class ProductEndpoints
{
    internal static async Task<IResult> Get(string barcode, ProductsDb db, CancellationToken cancellationToken)
    {
        if (!IsBarcode(barcode))
            return InvalidBarcode();

        var product = await db.Products.AsNoTracking().SingleOrDefaultAsync(p => p.Barcode == barcode, cancellationToken);
        return product is null
            ? Results.Problem(statusCode: StatusCodes.Status404NotFound, title: "Продукт не найден.")
            : Results.Ok(ProductResponse.From(product));
    }

    internal static async Task<IResult> Search(string? q, int? limit, ProductsDb db, CancellationToken cancellationToken)
    {
        var query = SearchKey.Normalize(q ?? "");
        if (query.EnumerateRunes().Take(2).Count() < 2 || query.Contains('\0'))
            return Results.ValidationProblem(new Dictionary<string, string[]> { ["q"] = ["Введите минимум два допустимых символа."] });
        if (limit is < 1 or > 50)
            return Results.ValidationProblem(new Dictionary<string, string[]> { ["limit"] = ["Допустимое значение от 1 до 50."] });

        var pattern = "%" + query.Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_") + "%";
        var matches = await db.Products.AsNoTracking()
            .Where(p => EF.Functions.Like(p.SearchKey, pattern, "\\"))
            .OrderByDescending(p => p.Source == "phone")
            .ThenByDescending(p => EF.Functions.TrigramsSimilarity(p.SearchKey, query))
            .ThenBy(p => p.Name)
            .ThenBy(p => p.Id)
            .Take(limit ?? 20)
            .ToListAsync(cancellationToken);
        return Results.Ok(matches.Select(ProductResponse.From));
    }

    internal static Task<IResult> Put(string barcode, ProductInput input, ProductsDb db, CancellationToken cancellationToken) =>
        IsBarcode(barcode) ? Save(barcode, input, db, cancellationToken) : Task.FromResult(InvalidBarcode());

    internal static Task<IResult> Post(ProductInput input, ProductsDb db, CancellationToken cancellationToken) =>
        Save(null, input, db, cancellationToken);

    private static async Task<IResult> Save(string? barcode, ProductInput input, ProductsDb db, CancellationToken cancellationToken)
    {
        var name = input.Name?.Trim();
        var brand = input.Brand?.Trim();
        var errors = new Dictionary<string, string[]>();
        if (string.IsNullOrWhiteSpace(name) || name.Contains('\0') || name.EnumerateRunes().Count() > 200)
            errors["name"] = ["Укажите допустимое название длиной от 1 до 200 символов."];
        if (brand?.Contains('\0') is true)
            errors["brand"] = ["Бренд содержит недопустимый символ."];
        if (input.Kcal is null or < 0 or > 900)
            errors["kcal"] = ["Укажите калорийность от 0 до 900 ккал на 100 г."];
        ValidateNutrient(errors, "protein", input.Protein);
        ValidateNutrient(errors, "fat", input.Fat);
        ValidateNutrient(errors, "carbs", input.Carbs);

        if (errors.Count > 0)
            return Results.ValidationProblem(errors);

        var searchKey = SearchKey.Normalize(name!);
        var id = Guid.NewGuid();
        var inserted = await db.Products.FromSql($"""
            INSERT INTO products (id, barcode, name, brand, kcal, protein, fat, carbs, source, created_at, updated_at, search_key)
            VALUES ({id}, {barcode}, {name}, {brand}, {input.Kcal}, {input.Protein}, {input.Fat}, {input.Carbs},
                    'phone', statement_timestamp(), statement_timestamp(), {searchKey})
            ON CONFLICT DO NOTHING
            RETURNING *
            """).AsNoTracking().ToListAsync(cancellationToken);

        if (inserted.Count == 1)
            return Results.Created(barcode is null ? (string?)null : $"/products/{barcode}", ProductResponse.From(inserted[0]));

        // После конфликта новый запрос видит карточку, созданную параллельной транзакцией.
        var existing = barcode is null
            ? db.Products.Where(p => p.Barcode == null && p.Source == "phone" && p.SearchKey == searchKey)
            : db.Products.Where(p => p.Barcode == barcode);
        var existingId = await existing.Select(p => p.Id).SingleAsync(cancellationToken);
        var updated = await db.Products.FromSql($"""
            UPDATE products SET name = {name}, brand = {brand}, kcal = {input.Kcal}, protein = {input.Protein},
                fat = {input.Fat}, carbs = {input.Carbs}, source = 'phone', updated_at = statement_timestamp(),
                search_key = {searchKey}
            WHERE id = {existingId}
            RETURNING *
            """).AsNoTracking().ToListAsync(cancellationToken);
        return Results.Ok(ProductResponse.From(updated.Single()));
    }

    private static void ValidateNutrient(Dictionary<string, string[]> errors, string field, decimal? value)
    {
        if (value is < 0 or > 100)
            errors[field] = ["Допустимое значение от 0 до 100 г на 100 г."];
    }

    private static bool IsBarcode(string barcode) =>
        barcode.Length is (8 or 12 or 13 or 14) && barcode.All(char.IsAsciiDigit);

    private static IResult InvalidBarcode() => Results.ValidationProblem(
        new Dictionary<string, string[]> { ["barcode"] = ["Штрихкод должен содержать 8, 12, 13 или 14 цифр."] });
}
