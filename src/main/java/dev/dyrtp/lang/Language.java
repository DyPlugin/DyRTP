package dev.dyrtp.lang;

public enum Language {
    EN("en"),
    TR("tr");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
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
