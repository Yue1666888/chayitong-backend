package com.chayitong.search;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SearchMatcher {

    public boolean matches(String source, String keyword) {
        String normalizedSource = source == null ? "" : source.toLowerCase(Locale.ROOT);
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        if (normalizedSource.contains(normalizedKeyword)) {
            return true;
        }
        int[] codePoints = normalizedKeyword.codePoints().toArray();
        if (codePoints.length < 2 || normalizedKeyword.codePoints().noneMatch(SearchMatcher::isCjk)) {
            return false;
        }
        for (int index = 0; index < codePoints.length - 1; index++) {
            String fragment = new String(codePoints, index, 2);
            if (normalizedSource.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }
}
