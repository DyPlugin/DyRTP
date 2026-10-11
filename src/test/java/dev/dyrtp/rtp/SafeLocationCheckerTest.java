package dev.dyrtp.rtp;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeLocationCheckerTest {
    private World world;
    private final SafetyRules rules = new SafetyRules(true, true, 0, 0, 8, true, Set.of(), Set.of(), Set.of());

    @BeforeEach void setup() {
        world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any())).thenReturn(true);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), Material.AIR));
    }

    private Block block(int x, int y, int z, Material material) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x); when(block.getY()).thenReturn(y); when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(material);
        when(block.getBiome()).thenReturn(Biome.PLAINS);
        when(block.isPassable()).thenReturn(material.isAir());
        return block;
    }

    private WorldProfile profile(int min, int max) { return new WorldProfile(world, false, 0, 0, 0, 100, "square", min, max, List.of()); }

    @Test void neverPlacesHeadOutsideWorldHeight() {
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(318);
        Block ground = block(0, 318, 0, Material.STONE);
        when(world.getBlockAt(0, 318, 0)).thenReturn(ground);
        assertNull(new SafeLocationChecker().findSafe(profile(-64, 319), rules, 0, 0));
    }

    @Test void landingAtConfiguredMinimumYRemainsValid() {
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(63);
        Block ground = block(0, 63, 0, Material.STONE);
        when(world.getBlockAt(0, 63, 0)).thenReturn(ground);
        assertNotNull(new SafeLocationChecker().findSafe(profile(64, 100), rules, 0, 0));
    }

    @Test void endVoidHasNoSafeLanding() {
        when(world.getEnvironment()).thenReturn(World.Environment.THE_END);
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(-64);
        assertNull(new SafeLocationChecker().findSafe(profile(-64, 319), rules, 0, 0));
    }
    @Test void netherScansForHeadRoomRatherThanUsingBedrockRoofAsSurface() {
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(world.getMinHeight()).thenReturn(0); when(world.getMaxHeight()).thenReturn(256);
        Block ground = block(0, 5, 0, Material.NETHERRACK);
        when(world.getBlockAt(0, 5, 0)).thenReturn(ground);
        Location safe = new SafeLocationChecker().findSafe(profile(0, 20), rules, 0, 0);
        assertNotNull(safe); assertEquals(6, safe.getY());
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class));
    }

    @Test void revalidationRejectsChangedGroundAndSolidHeadSpace() {
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(63);
        Block ground = block(0, 63, 0, Material.STONE);
        when(world.getBlockAt(0, 63, 0)).thenReturn(ground);
        SafeLocationChecker checker = new SafeLocationChecker();
        Location target = new Location(world, 0.5, 64, 0.5);
        assertTrue(checker.isSafeAt(profile(-64, 100), rules, target));
        Block obstruction = block(0, 65, 0, Material.STONE);
        when(world.getBlockAt(0, 65, 0)).thenReturn(obstruction);
        assertFalse(checker.isSafeAt(profile(-64, 100), rules, target));
        when(world.getBlockAt(0, 65, 0)).thenAnswer(invocation -> block(0, 65, 0, Material.AIR));
        when(ground.getType()).thenReturn(Material.LAVA);
        assertFalse(checker.isSafeAt(profile(-64, 100), rules, target));
    }

    @Test void redirectedDestinationChecksPlayerWidthAcrossAdjacentBlock() {
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(63);
        Block ground = block(0, 63, 0, Material.STONE);
        when(world.getBlockAt(0, 63, 0)).thenReturn(ground);
        Block adjacentWall = block(-1, 64, 0, Material.STONE);
        when(world.getBlockAt(-1, 64, 0)).thenReturn(adjacentWall);
        assertFalse(new SafeLocationChecker().isSafeAt(profile(-64, 100), rules, new Location(world, 0.1, 64, 0.5)));
    }
}
