using System.Text;

namespace CalorieTraining.Server;

/// <summary>Нормализует названия, марки и поисковые запросы.</summary>
public static class SearchKey
{
    /// <summary>Убирает пунктуацию, сохраняя десятичный разделитель и процент после числа.</summary>
    public static string Normalize(string value)
    {
        var key = new StringBuilder(value.Length);
        var separator = false;
        var whitespaceOnly = true;
        for (var i = 0; i < value.Length; i++)
        {
            var character = value[i];
            if (character is '.' or ',' && i > 0 && i + 1 < value.Length &&
                char.IsDigit(value[i - 1]) && char.IsDigit(value[i + 1]))
                character = '.';
            else if (character == '%' && whitespaceOnly && key.Length > 0 && char.IsDigit(key[^1]))
                separator = false;
            else if (char.IsWhiteSpace(character) || char.IsPunctuation(character))
            {
                separator = true;
                whitespaceOnly &= char.IsWhiteSpace(character);
                continue;
            }

            if (separator && key.Length > 0)
                key.Append(' ');
            key.Append(character);
            separator = false;
            whitespaceOnly = true;
        }

        return key.ToString().ToLowerInvariant().Replace('ё', 'е');
    }

    internal static string ForProduct(string name, string? brand)
    {
        var key = Normalize(name);
        if (string.IsNullOrEmpty(brand))
            return key;

        var nameWords = key.Split(' ', StringSplitOptions.RemoveEmptyEntries).ToHashSet(StringComparer.Ordinal);
        var brandWords = Normalize(brand).Split(' ', StringSplitOptions.RemoveEmptyEntries)
            .Where(word => !nameWords.Contains(word));
        return string.Join(' ', brandWords.Prepend(key).Where(word => word.Length > 0));
    }

    internal static string CollapseWhitespace(string value) =>
        string.Join(' ', value.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries));
}
