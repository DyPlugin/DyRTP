package dev.dyrtp;

import dev.dyrtp.api.DyRTPAPI;
import dev.dyrtp.cooldown.CooldownService;
import dev.dyrtp.economy.EconomyService;
import dev.dyrtp.hook.PlaceholderHook;
import dev.dyrtp.lang.Language;
import dev.dyrtp.lang.MessageService;
import dev.dyrtp.rank.RankRules;
import dev.dyrtp.rtp.RtpService;
import dev.dyrtp.storage.PlayerDataStore;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Release110Test {
    @TempDir Path temp;

    private static Player withPermissions(String... nodes) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Set<PermissionAttachmentInfo> infos = new HashSet<>();
        for (String node : nodes) {
            boolean value = !node.startsWith("-");
            PermissionAttachmentInfo info = mock(PermissionAttachmentInfo.class);
            when(info.getPermission()).thenReturn(value ? node : node.substring(1));
            when(info.getValue()).thenReturn(value);
            infos.add(info);
        }
        when(player.getEffectivePermissions()).thenReturn(infos);
        return player;
    }

    private static DyRTP plugin(YamlConfiguration config) {
        DyRTP plugin = mock(DyRTP.class);
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }

    // Rank cooldowns and prices
    @Test void lowestRankPermissionReplacesTheConfiguredCooldownAndPrice() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("cooldown.seconds", 300);
        RankRules rules = new RankRules(plugin(config));
        assertEquals(300, rules.cooldownSeconds(withPermissions()));
        assertEquals(60, rules.cooldownSeconds(withPermissions("dyrtp.cooldown.120", "dyrtp.cooldown.60")));
        assertEquals(900, rules.cooldownSeconds(withPermissions("dyrtp.cooldown.900")), "a rank may also be stricter");
        assertEquals(300, rules.cooldownSeconds(withPermissions("-dyrtp.cooldown.10", "dyrtp.cooldown.abc", "dyrtp.cooldown.")),
                "denied and non-numeric permissions are ignored");
        assertEquals(25D, rules.price(withPermissions("dyrtp.price.50", "dyrtp.price.25"), 100D));
        assertEquals(0D, rules.price(withPermissions("dyrtp.price.0"), 100D));
        assertEquals(100D, rules.price(withPermissions(), 100D));
        config.set("cooldown.permission-overrides", false);
        config.set("economy.permission-prices", false);
        assertEquals(300, rules.cooldownSeconds(withPermissions("dyrtp.cooldown.60")));
        assertEquals(100D, rules.price(withPermissions("dyrtp.price.25"), 100D));
    }

    @Test void highestRadiusPermissionLetsAPlayerTravelFarther() {
        YamlConfiguration config = new YamlConfiguration();
        RankRules rules = new RankRules(plugin(config));
        assertEquals(0, rules.maxRadius(withPermissions()));
        assertEquals(10000, rules.maxRadius(withPermissions("dyrtp.radius.3000", "dyrtp.radius.10000")));
        config.set("rtp.permission-radius", false);
        assertEquals(0, rules.maxRadius(withPermissions("dyrtp.radius.10000")));
        var profile = new dev.dyrtp.rtp.WorldProfile(null, false, 0, 0, 500, 5000, "square", -64, 320, List.of());
        assertEquals(10000, profile.withMaxRadius(10000).maxRadius());
        assertEquals(3000, profile.withMaxRadius(3000).maxRadius(), "a rank may also shorten the distance");
        assertSame(profile, profile.withMaxRadius(0));
        assertSame(profile, profile.withMaxRadius(400), "a radius inside min-radius is ignored");
    }

    @Test void cooldownStartsWithTheRankDurationAndPriceUsesTheRankPrice() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("cooldown.enabled", true);
        config.set("cooldown.seconds", 300);
        config.set("economy.cost", 100D);
        DyRTP plugin = plugin(config);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        Player vip = withPermissions("dyrtp.cooldown.30", "dyrtp.price.10");
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        CooldownService cooldowns = new CooldownService(plugin);
        cooldowns.start(vip, world);
        long remaining = cooldowns.remainingSeconds(vip, world);
        assertTrue(remaining > 25 && remaining <= 30, "remaining " + remaining);
        assertEquals(10D, new EconomyService(plugin).cost(vip));
    }

    // Languages
    @Test void everyLanguageFileHasTheSameKeysAndPlaceholders() throws Exception {
        YamlConfiguration english = load("messages_en.yml");
        Pattern placeholder = Pattern.compile("\\{[a-z-]+}");
        for (Language language : Language.values()) {
            YamlConfiguration other = load(language.fileName());
            assertEquals(english.getKeys(true), other.getKeys(true), language.fileName());
            for (String key : english.getKeys(true)) {
                if (english.isConfigurationSection(key)) continue;
                Set<String> expected = placeholder.matcher(String.valueOf(english.get(key))).results().map(MatchResult::group).collect(Collectors.toSet());
                Set<String> actual = placeholder.matcher(String.valueOf(other.get(key))).results().map(MatchResult::group).collect(Collectors.toSet());
                assertEquals(expected, actual, language.fileName() + " " + key);
            }
            assertTrue(other.getString("value.language-" + language.code(), "").length() > 1, language.code());
        }
    }

    @Test void clientLocalePicksTheMatchingLanguageAndFallsBackToEnglish() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("language.default", "auto");
        DyRTP plugin = plugin(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        PlayerDataStore store = mock(PlayerDataStore.class);
        MessageService messages = new MessageService(plugin, store);
        Map<String, Language> expected = Map.of("de_de", Language.DE, "es_mx", Language.ES, "ru_ru", Language.RU, "tr_tr", Language.TR, "fr_fr", Language.EN);
        expected.forEach((locale, language) -> {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getLocale()).thenReturn(locale);
            assertEquals(language, messages.language(player), locale);
        });
        config.set("language.default", "ru");
        assertEquals(Language.RU, messages.language(mock(Player.class)));
    }

    @Test void configAcceptsTheNewLanguagesOnly() throws Exception {
        YamlConfiguration config = load("config.yml");
        for (String language : List.of("auto", "en", "tr", "de", "es", "ru")) {
            config.set("language.default", language);
            ConfigValidator.validate(config);
        }
        config.set("language.default", "fr");
        assertThrows(Exception.class, () -> ConfigValidator.validate(config));
    }

    @Test void untouchedOldLanguageListsUpgradeButCustomTextStays() throws Exception {
        DyRTP plugin = mock(DyRTP.class, CALLS_REAL_METHODS);
        YamlConfiguration defaults = new YamlConfiguration(), existing = new YamlConfiguration();
        defaults.set("help", "&a/dyrtp language <auto|en|tr|de|es|ru> &7- Set your language preference");
        existing.set("help", "&a/dyrtp language <auto|en|tr> &7- Set your language preference");
        defaults.set("invalid", "{prefix} &cAvailable languages: &fauto&7, &fen&7, &ftr&7, &fde&7, &fes&7, &fru&c.");
        existing.set("invalid", "{prefix} &cAvailable languages: &fauto&7, &fen&7, &ftr&c.");
        defaults.set("custom", "&a/dyrtp language <auto|en|tr|de|es|ru> &7- Set your language preference");
        existing.set("custom", "&a/dyrtp language <auto|en|tr> &7- My own words");
        var method = DyRTP.class.getDeclaredMethod("copyMissing", ConfigurationSection.class, ConfigurationSection.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(plugin, defaults, existing));
        assertEquals(defaults.getString("help"), existing.getString("help"));
        assertEquals(defaults.getString("invalid"), existing.getString("invalid"));
        assertEquals("&a/dyrtp language <auto|en|tr> &7- My own words", existing.getString("custom"));
    }

    // Biome selection
    @Test void biomeChoicesFollowTheConfiguredListAndSkipBlacklistedBiomes() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("biome-selection.biomes", List.of("DESERT", "cherry_grove", "minecraft:jungle", "ocean", "custom"));
        config.set("rtp.safety.blacklisted-biomes", List.of("OCEAN"));
        DyRTP plugin = plugin(config);
        assertEquals(List.of("cherry_grove", "desert", "jungle"), dev.dyrtp.rtp.BiomeChoices.available(plugin));
        assertEquals("cherry_grove", dev.dyrtp.rtp.BiomeChoices.match(plugin, "Cherry Grove"));
        assertEquals("desert", dev.dyrtp.rtp.BiomeChoices.match(plugin, "DESERT"));
        assertNull(dev.dyrtp.rtp.BiomeChoices.match(plugin, "ocean"), "blacklisted biomes cannot be chosen");
        assertNull(dev.dyrtp.rtp.BiomeChoices.match(plugin, "plains"), "only listed biomes can be chosen");
    }

    @Test void aChosenBiomeNarrowsTheSearchButKeepsTheWorldsOwnBiomeList() {
        var open = new dev.dyrtp.rtp.SafetyRules(true, true, 0, 0, 8, true, Set.of(), Set.of("OCEAN"), Set.of());
        assertEquals(Set.of("DESERT"), open.withBiome("desert").allowedBiomes());
        assertSame(open, open.withBiome(null));
        var limited = new dev.dyrtp.rtp.SafetyRules(true, true, 0, 0, 8, true, Set.of(), Set.of(), Set.of("PLAINS", "FOREST"));
        assertEquals(Set.of("FOREST"), limited.withBiome("forest").allowedBiomes());
        assertFalse(limited.withBiome("desert").allowedBiomes().contains("DESERT"), "a biome outside the world's list never qualifies");
    }

    // MiniMessage
    @Test void miniMessageTagsWorkNextToAmpersandCodes() {
        assertEquals("§aHello", dev.dyrtp.util.Colors.color("<green>Hello"));
        assertEquals("§aHello", dev.dyrtp.util.Colors.color("&aHello"));
        assertEquals("§7[§aDyRTP§7] §cNo", dev.dyrtp.util.Colors.color("&7[&aDyRTP&7] <red>No"));
        assertTrue(dev.dyrtp.util.Colors.color("<gradient:#ff0000:#0000ff>Gradient</gradient>").contains("§x"), "gradients become hex colours");
        assertTrue(dev.dyrtp.util.Colors.color("<#123456>Hex").startsWith("§x"), "a colour outside the 16 standard ones stays hex");
        assertEquals("1 < 2 and 3 > 2", dev.dyrtp.util.Colors.color("1 < 2 and 3 > 2"), "text without tags is left alone");
        assertEquals("§cUnknown: <red>x", dev.dyrtp.util.Colors.color("<red>Unknown: " + dev.dyrtp.util.Colors.escapeTags("<red>x")),
                "tags typed by a player are shown, not applied");
        assertEquals("1 <2", dev.dyrtp.util.Colors.color("1 " + dev.dyrtp.util.Colors.escapeTags("<2")),
                "an escaped '<' without a tag is shown without the backslash");
    }

    // PlaceholderAPI
    @Test void placeholdersReportCooldownPriceReadinessAndWorld() {
        YamlConfiguration config = new YamlConfiguration();
        DyRTP plugin = plugin(config);
        RtpService rtp = mock(RtpService.class);
        EconomyService economy = mock(EconomyService.class);
        when(plugin.getRtpService()).thenReturn(rtp);
        when(plugin.getEconomy()).thenReturn(economy);
        Player player = mock(Player.class);
        when(player.getPlayer()).thenReturn(player);
        World world = mock(World.class);
        when(world.getName()).thenReturn("survival");
        when(rtp.cooldownRemaining(player)).thenReturn(90L);
        when(rtp.formatCooldown(player, 90L)).thenReturn("1m 30s");
        when(rtp.price(player)).thenReturn(250D);
        when(economy.format(250D)).thenReturn("$250.00");
        when(rtp.destinationWorld(player)).thenReturn(world);
        PlaceholderHook hook = new PlaceholderHook(plugin);
        assertEquals("dyrtp", hook.getIdentifier());
        assertEquals("90", hook.onRequest(player, "cooldown"));
        assertEquals("1m 30s", hook.onRequest(player, "cooldown_formatted"));
        assertEquals("false", hook.onRequest(player, "ready"));
        assertEquals("250", hook.onRequest(player, "price"));
        assertEquals("$250.00", hook.onRequest(player, "price_formatted"));
        assertEquals("survival", hook.onRequest(player, "world"));
        when(rtp.cooldownRemaining(player)).thenReturn(0L);
        assertEquals("true", hook.onRequest(player, "ready"));
        assertEquals("", hook.onRequest(player, "cooldown_formatted"));
        assertNull(hook.onRequest(player, "unknown"));
        assertEquals("", hook.onRequest(null, "cooldown"));
    }

    // API
    @Test void apiRefusesCallsWhileDyRtpIsNotEnabled() {
        assertFalse(DyRTPAPI.isAvailable());
        assertThrows(IllegalStateException.class, () -> DyRTPAPI.getPrice(mock(Player.class)));
    }

    // Packaging
    @Test void descriptorAndConfigDeclareTheNewOptions() throws Exception {
        YamlConfiguration descriptor = load("plugin.yml");
        assertTrue(descriptor.getStringList("softdepend").contains("PlaceholderAPI"));
        YamlConfiguration config = load("config.yml");
        assertTrue(config.getBoolean("cooldown.permission-overrides"));
        assertTrue(config.getBoolean("economy.permission-prices"));
        assertEquals("", config.getString("first-join-rtp.world"));
        assertEquals("", config.getString("death-rtp.world"));
        assertTrue(config.getBoolean("integrations.placeholderapi.enabled"));
        assertTrue(config.getBoolean("metrics.enabled"));
        assertEquals(34913, DyRTP.BSTATS_ID);
        assertEquals("dev.dyrtp.lib.bstats.bukkit", dev.dyrtp.lib.bstats.bukkit.Metrics.class.getPackageName());
    }

    private YamlConfiguration load(String resource) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
