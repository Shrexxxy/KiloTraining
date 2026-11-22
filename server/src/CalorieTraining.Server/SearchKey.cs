namespace CalorieTraining.Server;

/// <summary>Нормализует названия и поисковые запросы.</summary>
public static class SearchKey
{
    /// <summary>Приводит строку к нижнему регистру, заменяет ё и схлопывает пробелы.</summary>
    public static string Normalize(string value) =>
        string.Join(' ', value.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries))
            .ToLowerInvariant().Replace('ё', 'е');
}
