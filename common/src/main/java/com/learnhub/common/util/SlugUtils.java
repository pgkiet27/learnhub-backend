package com.learnhub.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

// create slug for Course URL, Category URL
public final class SlugUtils {
    private SlugUtils() {
    }

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-{2,}");

    public static String toSlug(String input) {
        if (input == null || input.isBlank())
            return "";

        // Normalize - separate accented characters into base + accent
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);

        // Strip the leftover combining accent marks (e.g. Vietnamese diacritics)
        // instead of letting them fall through to NON_LATIN and turn into stray hyphens
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");

        // lowercase
        String lower = withoutDiacritics.toLowerCase();

        // delete character is not alphabet, number, or strike
        String slug = NON_LATIN.matcher(lower).replaceAll("-");

        // merge multiple strike into 1
        slug = MULTI_HYPHEN.matcher(slug).replaceAll("-");

        // delete strike at beginning and ending
        return slug.strip().replaceAll("^-|-$", "");
    }

    // append a short random-ish suffix when the base slug already exists
    public static String toUniqueSlug(String input) {
        String slug = toSlug(input);
        String suffix = Long.toHexString(System.currentTimeMillis()).substring(8);
        return slug + "-" + suffix;
    }
}
