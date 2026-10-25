package dev.dyrtp.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Only loaded when the server ships Adventure (Paper, Purpur); see {@link Colors}. */
final class MiniMessageBridge {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private MiniMessageBridge() {
    }

    static String toLegacy(String text) {
        return LEGACY.serialize(MINI_MESSAGE.deserialize(text));
    }
}
