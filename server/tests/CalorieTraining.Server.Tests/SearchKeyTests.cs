using AwesomeAssertions;

namespace CalorieTraining.Server.Tests;

/// <summary>Проверяет нормализацию поискового ключа.</summary>
public sealed class SearchKeyTests
{
    /// <summary>Проверяет регистр, пробельные символы и замену ё.</summary>
    [Theory]
    [InlineData("  ТВОРОГ   Зернёный ", "творог зерненый")]
    [InlineData("Ёлка\t зелёная\n", "елка зеленая")]
    public void NormalizesName(string input, string expected)
    {
        SearchKey.Normalize(input).Should().Be(expected);
    }
}
