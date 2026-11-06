package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;

/**
 * /rtp near and /rtp far: a search between two distances from the world's center instead of the world's own
 * min-radius and max-radius. Both are off until distance-presets.enabled is true; players need dyrtp.near or dyrtp.far.
 * The world border still limits the farthest distance.
 */
public record DistancePreset(String name, int minRadius, int maxRadius) {

    public static final String NEAR = "near";
    public static final String FAR = "far";

    public static boolean enabled(DyRTP plugin) {
        return plugin.getConfig().getBoolean("distance-presets.enabled", false);
    }

    /** The preset called near or far, or null when presets are off or the name is unknown. */
    public static DistancePreset load(DyRTP plugin, String name) {
        if (!enabled(plugin) || (!NEAR.equals(name) && !FAR.equals(name))) return null;
        boolean near = NEAR.equals(name);
        String path = "distance-presets." + name + ".";
        int min = Math.max(0, plugin.getConfig().getInt(path + "min-radius", near ? 100 : 5000));
        int max = Math.max(min + 1, plugin.getConfig().getInt(path + "max-radius", near ? 1000 : 20000));
        return new DistancePreset(name, min, max);
    }

    public String permission() {
        return "dyrtp." + name;
    }
}
