package dev.dyrtp.util;

import org.bukkit.ChatColor;

import java.util.regex.Pattern;

public final class Colors {
    /** A MiniMessage tag such as <green>, <#55ff55>, <gradient:...> or </bold>. */
    private static final Pattern TAG = Pattern.compile("<[a-zA-Z#/!][^<>]*>");
    private static final boolean MINI_MESSAGE = present("net.kyori.adventure.text.minimessage.MiniMessage")
            && present("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");

    private Colors() {
    }

    /**
     * Applies '&' colour codes and, when the server ships Adventure (Paper, Purpur), MiniMessage tags.
     * Text without tags is never handed to MiniMessage; on Spigot only '&' codes apply.
     */
    public static String color(String text) {
        if (text == null) {
            return "";
        }
        String result = text;
        if (MINI_MESSAGE && (TAG.matcher(result).find() || result.contains("\\<"))) {
            try {
                result = MiniMessageBridge.toLegacy(result);
            } catch (RuntimeException | LinkageError ignored) {
                // Malformed tags keep the original text with '&' codes only.
            }
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    /**
     * Makes text from players or the server safe to place inside a message: MiniMessage tags in it
     * are shown as typed instead of being applied. Without Adventure the text is returned unchanged.
     */
    public static String escapeTags(String text) {
        if (!MINI_MESSAGE || text == null || text.indexOf('<') < 0) return text;
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    public static String strip(String text) {
        return ChatColor.stripColor(color(text));
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, Colors.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }
}
