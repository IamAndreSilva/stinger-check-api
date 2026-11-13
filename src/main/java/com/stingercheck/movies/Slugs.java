package com.stingercheck.movies;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** URL slugs for movie pages, e.g. "Amélie" -> "amelie", "Spider-Man: No Way Home" -> "spider-man-no-way-home". */
public final class Slugs {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private Slugs() {}

    /** Returns an empty string when the title has no Latin letters or digits (e.g. a Japanese title). */
    public static String of(String title) {
        if (title == null) {
            return "";
        }

        String withoutAccents = COMBINING_MARKS.matcher(Normalizer.normalize(title, Normalizer.Form.NFD)).replaceAll("");
        String dashed = NON_ALPHANUMERIC.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll("-");
        return EDGE_DASHES.matcher(dashed).replaceAll("");
    }

    /** Path of a movie page in the UI: /movies/1726-iron-man, or /movies/1726 when the slug is empty. */
    public static String moviePath(int tmdbId, String title) {
        String slug = of(title);

        if (slug.isEmpty()) {
            return "/movies/" + tmdbId;
        }

        return "/movies/" + tmdbId + "-" + slug;
    }
}
