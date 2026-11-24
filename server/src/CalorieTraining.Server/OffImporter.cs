using System.Diagnostics;
using System.Globalization;
using System.IO.Compression;
using Microsoft.VisualBasic.FileIO;
using Npgsql;
using NpgsqlTypes;

namespace CalorieTraining.Server;

internal static class OffImporter
{
    private const string DefaultUrl = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz";
    private const int BatchSize = 5000;
    private static readonly string[] Columns =
    [
        "code", "product_name", "brands", "countries_tags", "energy-kcal_100g",
        "proteins_100g", "fat_100g", "carbohydrates_100g", "last_modified_t"
    ];

    internal static async Task RunCommandAsync(string? connectionString, string[] args)
    {
        if (string.IsNullOrWhiteSpace(connectionString))
            throw new ArgumentException("Укажите строку подключения ConnectionStrings:Products.");

        var sourceArgs = args.Where(arg => arg != "--only-russia").ToArray();
        var russiaFlags = args.Length - sourceArgs.Length;
        if (russiaFlags > 1 || sourceArgs.Length != 0 &&
            (sourceArgs.Length != 2 || sourceArgs[0] is not ("--file" or "--url") ||
             string.IsNullOrWhiteSpace(sourceArgs[1]) || sourceArgs[1].StartsWith("--", StringComparison.Ordinal)))
            throw new ArgumentException("Использование: import-off [--only-russia] [--file <путь> | --url <адрес>].");

        var file = sourceArgs is ["--file", var path] ? path : null;
        var url = sourceArgs is ["--url", var address] ? address : null;
        var stopwatch = Stopwatch.StartNew();
        var result = await RunAsync(connectionString, file, url, CancellationToken.None, onlyRussia: russiaFlags == 1);
        Console.WriteLine($"Прочитано: {result.Read}; подходит: {result.Eligible}; добавлено: {result.Added}; обновлено: {result.Updated}.");
        Console.WriteLine($"Пропущено (свои карточки): {result.Phone}; без изменений: {result.Unchanged}; повторы в пакете: {result.Duplicates}.");
        var reasons = string.Join(", ", result.Rejected.Select(pair => $"{pair.Key}: {pair.Value}"));
        Console.WriteLine($"Отброшено: {result.Read - result.Eligible}" + (reasons.Length == 0 ? "." : $" ({reasons})."));
        Console.WriteLine($"Время: {stopwatch.Elapsed:c}.");
    }

    internal static async Task<OffImportResult> RunAsync(string connectionString, string? file, string? url,
        CancellationToken cancellationToken, bool onlyRussia = false)
    {
        var address = url ?? DefaultUrl;
        using var http = new HttpClient();
        using var response = file is null
            ? await http.GetAsync(address, HttpCompletionOption.ResponseHeadersRead, cancellationToken)
            : null;
        await using var input = file is null
            ? await response!.EnsureSuccessStatusCode().Content.ReadAsStreamAsync(cancellationToken)
            : File.OpenRead(file);
        await using var download = url is null ? null : new FileStream(
            Path.Combine(Path.GetTempPath(), Path.GetRandomFileName()),
            FileMode.CreateNew, FileAccess.ReadWrite, FileShare.None, 65536,
            FileOptions.Asynchronous | FileOptions.DeleteOnClose);
        if (download is not null)
        {
            await input.CopyToAsync(download, cancellationToken);
            download.Position = 0;
        }

        var sourcePath = file ?? new Uri(address).AbsolutePath;
        await using var gzip = sourcePath.EndsWith(".gz", StringComparison.OrdinalIgnoreCase)
            ? new GZipStream(download ?? input, CompressionMode.Decompress, leaveOpen: true)
            : null;
        using var parser = new TextFieldParser(gzip ?? download ?? input, System.Text.Encoding.UTF8, true, true)
        {
            Delimiters = ["\t"],
            HasFieldsEnclosedInQuotes = true,
            TrimWhiteSpace = false
        };

        var header = parser.ReadFields() ?? throw new InvalidDataException("В выгрузке отсутствует заголовок.");
        var positions = Columns.Select(column => Array.IndexOf(header, column)).ToArray();
        var missing = Columns.Where((_, index) => positions[index] < 0).ToArray();
        if (missing.Length > 0)
            throw new InvalidDataException($"В выгрузке отсутствуют колонки: {string.Join(", ", missing)}.");

        await using var connection = new NpgsqlConnection(connectionString);
        await connection.OpenAsync(cancellationToken);
        await ExecuteAsync(connection, """
            CREATE TEMP TABLE off_batch (
                barcode text PRIMARY KEY, name text NOT NULL, brand text,
                kcal numeric(7,2) NOT NULL, protein numeric(7,2), fat numeric(7,2), carbs numeric(7,2),
                search_key text NOT NULL, ru boolean NOT NULL
            )
            """, cancellationToken);

        var batch = new Dictionary<string, OffProduct>(BatchSize, StringComparer.Ordinal);
        var rejected = new Dictionary<string, long>();
        long read = 0, eligible = 0, added = 0, updated = 0, phone = 0, unchanged = 0, duplicates = 0;
        while (!parser.EndOfData)
        {
            cancellationToken.ThrowIfCancellationRequested();
            read++;
            string[]? fields;
            try
            {
                fields = parser.ReadFields();
            }
            catch (MalformedLineException)
            {
                fields = null;
            }

            var reason = Parse(fields, header.Length, positions, onlyRussia, out var product);
            if (reason is not null)
            {
                rejected[reason] = rejected.GetValueOrDefault(reason) + 1;
                continue;
            }

            eligible++;
            if (!batch.TryAdd(product!.Barcode, product))
            {
                duplicates++;
                if (product.Modified >= batch[product.Barcode].Modified)
                    batch[product.Barcode] = product;
            }

            if (batch.Count == BatchSize)
                await FlushAsync();
        }

        if (batch.Count > 0)
            await FlushAsync();

        return new OffImportResult(read, eligible, added, updated, phone, unchanged, rejected, duplicates);

        async Task FlushAsync()
        {
            var counts = await SaveBatchAsync(connection, batch.Values, cancellationToken);
            added += counts.Added;
            updated += counts.Updated;
            phone += counts.Phone;
            unchanged += batch.Count - counts.Added - counts.Updated - counts.Phone;
            batch.Clear();
        }
    }

    private static string? Parse(string[]? fields, int width, int[] positions, bool onlyRussia, out OffProduct? product)
    {
        product = null;
        if (fields is null || fields.Length != width)
            return "формат";

        var taggedRussia = fields[positions[3]].Split(',').Contains("en:russia", StringComparer.Ordinal);
        if (onlyRussia && !taggedRussia)
            return "страна";

        var barcode = fields[positions[0]];
        if (barcode.Length is not (8 or 12 or 13 or 14) || !barcode.All(char.IsAsciiDigit))
            return "штрихкод";

        var name = fields[positions[1]].Trim();
        if (name.Length is 0 or > 200)
            return "название";

        if (ParseNutrition(fields[positions[4]], 900) is not { } kcal)
            return "калорийность";

        var brand = fields[positions[2]].Trim();
        if (name.Contains('\0') || brand.Contains('\0'))
            return "формат";

        long.TryParse(fields[positions[8]], NumberStyles.Integer, CultureInfo.InvariantCulture, out var modified);
        var ru = taggedRussia || (barcode.Length == 13 && barcode[0] == '4' && barcode[1] == '6');
        product = new OffProduct(barcode, name, brand.Length == 0 ? null : brand, kcal,
            ParseNutrition(fields[positions[5]], 100), ParseNutrition(fields[positions[6]], 100),
            ParseNutrition(fields[positions[7]], 100), SearchKey.Normalize(name), ru, modified);
        return null;
    }

    private static decimal? ParseNutrition(string value, decimal maximum) =>
        decimal.TryParse(value, NumberStyles.Float, CultureInfo.InvariantCulture, out var number) &&
        number >= 0 && number <= maximum ? number : null;

    private static async Task<(int Added, int Updated, long Phone)> SaveBatchAsync(NpgsqlConnection connection,
        IEnumerable<OffProduct> batch, CancellationToken cancellationToken)
    {
        await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
        await using (var writer = await connection.BeginBinaryImportAsync("""
            COPY off_batch (barcode, name, brand, kcal, protein, fat, carbs, search_key, ru)
            FROM STDIN (FORMAT BINARY)
            """, cancellationToken))
        {
            foreach (var product in batch)
            {
                await writer.StartRowAsync(cancellationToken);
                await writer.WriteAsync(product.Barcode, NpgsqlDbType.Text, cancellationToken);
                await writer.WriteAsync(product.Name, NpgsqlDbType.Text, cancellationToken);
                if (product.Brand is null)
                    await writer.WriteNullAsync(cancellationToken);
                else
                    await writer.WriteAsync(product.Brand, NpgsqlDbType.Text, cancellationToken);
                await writer.WriteAsync(product.Kcal, NpgsqlDbType.Numeric, cancellationToken);
                await WriteNutritionAsync(product.Protein);
                await WriteNutritionAsync(product.Fat);
                await WriteNutritionAsync(product.Carbs);
                await writer.WriteAsync(product.SearchKey, NpgsqlDbType.Text, cancellationToken);
                await writer.WriteAsync(product.Ru, NpgsqlDbType.Boolean, cancellationToken);
            }

            await writer.CompleteAsync(cancellationToken);

            Task WriteNutritionAsync(decimal? value) => value is { } number
                ? writer.WriteAsync(number, NpgsqlDbType.Numeric, cancellationToken)
                : writer.WriteNullAsync(cancellationToken);
        }

        // Уникальный индекс штрихкода частичный, поэтому предикат нужен и при обработке конфликта.
        var added = await ExecuteAsync(connection, """
            INSERT INTO products (id, barcode, name, brand, kcal, protein, fat, carbs, source, created_at, updated_at, search_key, ru)
            SELECT gen_random_uuid(), barcode, name, brand, kcal, protein, fat, carbs,
                   'openfoodfacts', now(), now(), search_key, ru
            FROM off_batch ORDER BY barcode
            ON CONFLICT (barcode) WHERE barcode IS NOT NULL DO NOTHING
            """, cancellationToken);

        var updated = await ExecuteAsync(connection, """
            UPDATE products AS p
            SET name = b.name, brand = b.brand, kcal = b.kcal, protein = b.protein,
                fat = b.fat, carbs = b.carbs, search_key = b.search_key, ru = b.ru, updated_at = now()
            FROM off_batch AS b
            WHERE p.barcode = b.barcode AND p.source = 'openfoodfacts'
              AND (p.name, p.brand, p.kcal, p.protein, p.fat, p.carbs, p.search_key, p.ru)
                  IS DISTINCT FROM (b.name, b.brand, b.kcal, b.protein, b.fat, b.carbs, b.search_key, b.ru)
            """, cancellationToken);

        await using var countPhone = new NpgsqlCommand("""
            SELECT count(*) FROM off_batch AS b JOIN products AS p USING (barcode) WHERE p.source = 'phone'
            """, connection);
        var phone = (long)(await countPhone.ExecuteScalarAsync(cancellationToken))!;
        await ExecuteAsync(connection, "TRUNCATE off_batch", cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return (added, updated, phone);
    }

    private static async Task<int> ExecuteAsync(NpgsqlConnection connection, string sql, CancellationToken cancellationToken)
    {
        await using var command = new NpgsqlCommand(sql, connection);
        return await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private sealed record OffProduct(string Barcode, string Name, string? Brand, decimal Kcal, decimal? Protein,
        decimal? Fat, decimal? Carbs, string SearchKey, bool Ru, long Modified);
}

internal sealed record OffImportResult(long Read, long Eligible, long Added, long Updated, long Phone,
    long Unchanged, IReadOnlyDictionary<string, long> Rejected, long Duplicates);
