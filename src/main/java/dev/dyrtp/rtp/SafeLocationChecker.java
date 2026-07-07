package dev.dyrtp.rtp;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

public class SafeLocationChecker {

    public Location findSafe(WorldProfile profile, SafetyRules rules, int x, int z) {
        World world = profile.world();
        if (!world.getWorldBorder().isInside(new Location(world, x + 0.5D, 64.0D, z + 0.5D))) {
            return null;
        }
        if (world.getEnvironment() == World.Environment.NETHER) {
            return findNether(profile, rules, x, z);
        }
        return findSurface(profile, rules, x, z);
    }

    private Location findSurface(WorldProfile profile, SafetyRules rules, int x, int z) {
        World world = profile.world();
        int surfaceY = surfaceY(world, x, z);
        if (surfaceY < profile.minY() || surfaceY > profile.maxY()) {
            return null;
        }

        Block ground = world.getBlockAt(x, surfaceY, z);
        Location safe = validLanding(profile, rules, ground);
        if (safe == null) {
            return null;
        }
        if (rules.surfaceOnly() && tooDeepComparedToNearbySurface(profile, rules, ground)) {
            return null;
        }
        return safe;
    }

    private Location findNether(WorldProfile profile, SafetyRules rules, int x, int z) {
        World world = profile.world();
        for (int y = profile.minY() + 1; y <= profile.maxY() - 1; y++) {
            Block ground = world.getBlockAt(x, y - 1, z);
            Location safe = validLanding(profile, rules, ground);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private Location validLanding(WorldProfile profile, SafetyRules rules, Block ground) {
        int y = ground.getY() + 1;
        if (y < profile.minY() || y > profile.maxY()) {
            return null;
        }
        if (unsafeGround(rules, ground)) {
            return null;
        }
        if (!rules.biomeAllowed(ground)) {
            return null;
        }
        Block feet = ground.getWorld().getBlockAt(ground.getX(), y, ground.getZ());
        Block head = ground.getWorld().getBlockAt(ground.getX(), y + 1, ground.getZ());
        if (rules.requireHeadRoom() && (!safeSpace(rules, feet) || !safeSpace(rules, head))) {
            return null;
        }
        return new Location(ground.getWorld(), ground.getX() + 0.5D, y, ground.getZ() + 0.5D);
    }

    private boolean unsafeGround(SafetyRules rules, Block block) {
        Material material = block.getType();
        String name = material.name();
        if (!material.isSolid() || rules.blacklisted(material)) {
            return true;
        }
        if (name.endsWith("_LEAVES") || name.contains("WATER") || name.contains("LAVA")) {
            return true;
        }
        return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private boolean safeSpace(SafetyRules rules, Block block) {
        Material material = block.getType();
        String name = material.name();
        if (rules.blacklisted(material) || name.contains("WATER") || name.contains("LAVA") || name.contains("FIRE")) {
            return false;
        }
        if (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return false;
        }
        return material.isAir() || block.isPassable();
    }

    private int surfaceY(World world, int x, int z) {
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    private boolean tooDeepComparedToNearbySurface(WorldProfile profile, SafetyRules rules, Block ground) {
        int radius = rules.nearbySurfaceRadius();
        int maxDrop = rules.maxNearbySurfaceDrop();
        if (radius == 0 || maxDrop == 0) {
            return false;
        }
        World world = profile.world();
        int groundY = ground.getY();
        int[][] offsets = {
                {radius, 0},
                {-radius, 0},
                {0, radius},
                {0, -radius},
                {radius, radius},
                {radius, -radius},
                {-radius, radius},
                {-radius, -radius}
        };
        for (int[] offset : offsets) {
            int nearbyY = surfaceY(world, ground.getX() + offset[0], ground.getZ() + offset[1]);
            if (nearbyY - groundY > maxDrop) {
                return true;
            }
        }
        return false;
    }
}
