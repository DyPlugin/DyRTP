package dev.dyrtp.util;

import org.bukkit.configuration.Configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-mostly string lists from the configuration as sets, without copying the list on every call.
 * {@code getStringList} builds a new list each time; hot paths such as placeholders, the final safety check and
 * queue refills ask for the same list many times. A cached set is reused while the configuration object and the
 * stored list are the same objects; a reload or {@code set(...)} replaces them, so changes are picked up at once.
 */
public final class ConfigCache {
    private record Entry(Configuration config, Object raw, Set<String> values) { }

    private static final Map<String, Entry> SETS = new ConcurrentHashMap<>();

    private ConfigCache() {
    }

    /** The list at {@code path} as an unmodifiable set, exactly as written. */
    public static Set<String> stringSet(Configuration config, String path) {
        return cached(config, path, false);
    }

    /** The list at {@code path} as an unmodifiable set of upper-case values; blank entries are left out. */
    public static Set<String> upperSet(Configuration config, String path) {
        return cached(config, path, true);
    }

    private static Set<String> cached(Configuration config, String path, boolean upper) {
        String key = upper ? "^" + path : path;
        Object raw = config.get(path);
        Entry entry = SETS.get(key);
        if (entry != null && entry.config == config && entry.raw == raw) return entry.values;
        Set<String> values = build(raw, upper);
        SETS.put(key, new Entry(config, raw, values));
        return values;
    }

    private static Set<String> build(Object raw, boolean upper) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return Set.of();
        Set<String> out = new HashSet<>();
        for (Object item : list) {
            // Same conversions as getStringList: strings and simple scalars.
            if (!(item instanceof String) && !(item instanceof Number) && !(item instanceof Boolean) && !(item instanceof Character)) continue;
            String text = String.valueOf(item);
            if (upper) {
                if (text.isBlank()) continue;
                text = text.toUpperCase(Locale.ROOT);
            }
            out.add(text);
        }
        return Set.copyOf(out);
    }
}
