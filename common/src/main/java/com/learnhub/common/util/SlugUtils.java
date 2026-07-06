package com.learnhub.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

// create slug for Course URL, Category URL
public final class SlugUtils {
    private SlugUtils() {
    }

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-{2,}");

    public static String toSlug(String input) {
        if (input == null || input.isBlank())
            return "";

        // Normalize - separate accented characters into base + accent
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);

        // lowercase
        String lower = normalized.toLowerCase();

        // delete character is not alphabet, number, or strike
        String slug = NON_LATIN.matcher(lower).replaceAll("-");

        // merge multiple strike into 1
        slug = MULTI_HYPHEN.matcher(slug).replaceAll("-");

        // delete strike at beginning and ending
        return slug.strip().replaceAll("^-|-$", "");
    }
}
