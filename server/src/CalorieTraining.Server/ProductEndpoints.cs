using System.Text.RegularExpressions;
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
        q ??= "";
        var length = q.EnumerateRunes().Take(101).Count();
        if (length > 100)
            return Results.ValidationProblem(new Dictionary<string, string[]> { ["q"] = ["Введите не более 100 символов."] });

        var query = SearchKey.Normalize(q);
        if (query.EnumerateRunes().Take(2).Count() < 2 || query.Contains('\0'))
            return Results.ValidationProblem(new Dictionary<string, string[]> { ["q"] = ["Введите минимум два допустимых символа."] });
        var words = query.Split(' ');
        if (words.Length > 8)
            return Results.ValidationProblem(new Dictionary<string, string[]> { ["q"] = ["Введите не более восьми слов."] });
        if (limit is < 1 or > 50)
            return Results.ValidationProblem(new Dictionary<string, string[]> { ["limit"] = ["Допустимое значение от 1 до 50."] });

        var candidates = db.Products.AsNoTracking();
        var longWords = words.Where(word => word.EnumerateRunes().Take(3).Count() == 3)
            .ToHashSet(StringComparer.Ordinal);
        if (longWords.Count == 0)
            candidates = candidates.Where(p => p.Source == "phone" || p.Ru);

        foreach (var word in words.Distinct(StringComparer.Ordinal))
        {
            if (longWords.Contains(word) && (word.Contains('.') || word.EndsWith('%')))
            {
                var numberPattern = NumberPattern(word);
                candidates = candidates.Where(p => Regex.IsMatch(p.SearchKey, numberPattern));
            }
            else if (longWords.Contains(word))
            {
                var pattern = "%" + EscapeLike(word) + "%";
                candidates = candidates.Where(p => EF.Functions.Like(p.SearchKey, pattern, "\\"));
            }
            else
            {
                var shortWord = word;
                if (shortWord.EndsWith('%'))
                {
                    var previousPercent = shortWord.Insert(shortWord.Length - 1, " ");
                    candidates = candidates.Where(p =>
                        p.SearchKey.IndexOf(shortWord) >= 0 || p.SearchKey.IndexOf(previousPercent) >= 0);
                }
                else
                    candidates = candidates.Where(p => p.SearchKey.IndexOf(shortWord) >= 0);
            }
        }

        const string separator = "[[:space:][:punct:]]";
        var startsName = "^" + separator + "*" + string.Join(separator + "+", words.Select(NamePattern));
        var startsWords = "^" + string.Concat(words.Select(word =>
            "(?= *(?:.* )?" + NumberPattern(word) + ")"));
        var startsWordsWithPunctuation = "^" + string.Concat(words.Select(word =>
            "(?=(?:.*" + separator + ")?" + NumberPattern(word) + ")"));
        var matches = await candidates
            .OrderByDescending(p => p.Source == "phone")
            .ThenByDescending(p => p.Ru)
            .ThenByDescending(p => Regex.IsMatch(p.Name, startsName, RegexOptions.IgnoreCase))
            .ThenByDescending(p => Regex.IsMatch(p.SearchKey, p.Barcode == null ? startsWordsWithPunctuation : startsWords))
            .ThenByDescending(p => EF.Functions.TrigramsSimilarity(p.SearchKey, query))
            .ThenBy(p => p.Name)
            .ThenBy(p => p.Id)
            .Take(limit ?? 20)
            .ToListAsync(cancellationToken);
        return Results.Ok(matches.Select(ProductResponse.From));
    }

    private static string EscapeLike(string word) =>
        word.Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_");

    private static string NamePattern(string word) => NumberPattern(word).Replace("е", "[её]");

    private static string NumberPattern(string word) =>
        Regex.Escape(word).Replace("\\.", "[.,]").Replace("%", "[[:space:]]*%");

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

        var searchKey = barcode is null
            ? SearchKey.CollapseWhitespace(name!).ToLowerInvariant().Replace('ё', 'е')
            : SearchKey.ForProduct(name!, brand);
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
