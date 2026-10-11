package dev.dyrtp.update;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    private int compare(String a, String b) throws Exception {
        Method method = ModrinthUpdateChecker.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);
        return (int) method.invoke(null, a, b);
    }
    @Test void stableBeatsNumberedBetaOfSameCore() throws Exception {
        assertTrue(compare("1.0.0", "1.0.0-Beta.2") > 0);
    }
    @Test void betaDoesNotBeatInstalledStable() throws Exception {
        assertTrue(compare("1.0.0-Beta.2", "1.0.0") < 0);
    }
    @Test void coreAndVisiblePrefixCompare() throws Exception {
        assertEquals(0, compare("v1.0.0", "1.0.0"));
        assertTrue(compare("1.0.1", "1.0.0-Beta") > 0);
    }
    @Test void releaseSelectionFiltersGameLoaderAndUsesVersionLink() {
        String json = """
            [
              {"id":"WrongGame","version_number":"9.0.0","version_type":"release","game_versions":["1.21"],"loaders":["paper"]},
              {"id":"WrongLoader","version_number":"8.0.0","version_type":"release","game_versions":["1.20.4"],"loaders":["fabric"]},
              {"id":"Beta","version_number":"7.0.0-Beta","version_type":"beta","game_versions":["1.20.4"],"loaders":["paper"]},
              {"id":"Stable1","version_number":"1.0.0","version_type":"release","game_versions":["1.20.4"],"loaders":["paper"]}
            ]
            """;
        var release = ModrinthUpdateChecker.selectRelease(json, "1.20.4", "paper").orElseThrow();
        assertEquals("1.0.0", release.version());
        assertEquals("https://modrinth.com/plugin/dyrtp/version/Stable1", release.url());
        assertTrue(ModrinthUpdateChecker.selectRelease(json, "26.3", "paper").isEmpty());
    }
    @Test void brokenFeedIsAnErrorRatherThanNoUpdate() {
        assertThrows(RuntimeException.class, () -> ModrinthUpdateChecker.selectRelease("invalid", "1.20.4", "paper"));
    }
}
