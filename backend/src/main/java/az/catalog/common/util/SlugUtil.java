package az.catalog.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * URL slug generation that keeps Azerbaijani, Turkish and Russian titles readable
 * instead of collapsing them into a stream of dashes.
 */
public final class SlugUtil {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\p{Alnum}-]");
    private static final Pattern MULTIPLE_DASH = Pattern.compile("-{2,}");
    private static final Pattern EDGE_DASH = Pattern.compile("^-|-$");

    /** Characters Normalizer cannot decompose into ASCII on their own. */
    private static final Map<Character, String> TRANSLITERATION = Map.ofEntries(
            Map.entry('ə', "e"), Map.entry('Ə', "e"),
            Map.entry('ı', "i"), Map.entry('İ', "i"),
            Map.entry('ğ', "g"), Map.entry('Ğ', "g"),
            Map.entry('ş', "s"), Map.entry('Ş', "s"),
            Map.entry('ç', "c"), Map.entry('Ç', "c"),
            Map.entry('ö', "o"), Map.entry('Ö', "o"),
            Map.entry('ü', "u"), Map.entry('Ü', "u"),
            Map.entry('ß', "ss"),
            Map.entry('æ', "ae"), Map.entry('ø', "o"), Map.entry('å', "a")
    );

    private static final String[][] CYRILLIC = {
            {"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "g"}, {"д", "d"}, {"е", "e"}, {"ё", "e"},
            {"ж", "zh"}, {"з", "z"}, {"и", "i"}, {"й", "y"}, {"к", "k"}, {"л", "l"}, {"м", "m"},
            {"н", "n"}, {"о", "o"}, {"п", "p"}, {"р", "r"}, {"с", "s"}, {"т", "t"}, {"у", "u"},
            {"ф", "f"}, {"х", "h"}, {"ц", "ts"}, {"ч", "ch"}, {"ш", "sh"}, {"щ", "sch"},
            {"ъ", ""}, {"ы", "y"}, {"ь", ""}, {"э", "e"}, {"ю", "yu"}, {"я", "ya"}
    };

    private SlugUtil() {
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String working = input.toLowerCase(Locale.ROOT);

        for (String[] pair : CYRILLIC) {
            if (working.contains(pair[0])) {
                working = working.replace(pair[0], pair[1]);
            }
        }

        StringBuilder transliterated = new StringBuilder(working.length());
        for (char c : working.toCharArray()) {
            String replacement = TRANSLITERATION.get(c);
            transliterated.append(replacement != null ? replacement : c);
        }

        String normalized = Normalizer.normalize(transliterated.toString(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[\\s_]+", "-");

        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        slug = MULTIPLE_DASH.matcher(slug).replaceAll("-");
        slug = EDGE_DASH.matcher(slug).replaceAll("");

        return slug.length() > 180 ? slug.substring(0, 180).replaceAll("-$", "") : slug;
    }

    /**
     * Appends {@code -2}, {@code -3}, … until {@code isTaken} reports the slug as free.
     */
    public static String uniqueSlug(String base, java.util.function.Predicate<String> isTaken) {
        String slug = slugify(base);
        if (slug.isEmpty()) {
            slug = "item";
        }
        if (!isTaken.test(slug)) {
            return slug;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = slug + "-" + suffix;
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        return slug + "-" + System.currentTimeMillis();
    }

    public static boolean isValidSlug(String slug) {
        return slug != null
                && !slug.isBlank()
                && slug.length() <= 180
                && slug.matches("[a-z0-9]+(-[a-z0-9]+)*");
    }
}
