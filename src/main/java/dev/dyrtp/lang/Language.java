package dev.dyrtp.lang;

import java.util.Arrays;
import java.util.List;

public enum Language {
    EN("en"),
    TR("tr"),
    DE("de"),
    ES("es"),
    RU("ru");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public String fileName() {
        return "messages_" + code + ".yml";
    }

    /** Language codes in display order, for validation, help and TAB. */
    public static List<String> codes() {
        return Arrays.stream(values()).map(Language::code).toList();
    }

    public static Language fromCode(String value, Language fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        for (Language language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        return fallback;
    }
}
