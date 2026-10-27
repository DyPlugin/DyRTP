package dev.dyrtp;

import dev.dyrtp.platform.Scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A mocked DyRTP whose scheduler is real, so scheduling goes through the server mock the tests set up. */
public final class TestPlugins {
    private TestPlugins() {
    }

    public static DyRTP plugin() {
        DyRTP plugin = mock(DyRTP.class);
        when(plugin.scheduler()).thenReturn(new Scheduler(plugin));
        return plugin;
    }
}
