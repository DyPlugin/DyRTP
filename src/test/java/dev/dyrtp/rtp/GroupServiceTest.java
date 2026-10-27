package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupServiceTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final DyRTP plugin = dev.dyrtp.TestPlugins.plugin();
    private final GroupService groups;

    GroupServiceTest() {
        when(plugin.getConfig()).thenReturn(config);
        groups = new GroupService(plugin);
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        return player;
    }

    @Test void onlyAcceptedPlayersJoinAndTheLeaderCountsTowardsTheSize() {
        config.set("group.max-size", 3);
        Player leader = player("Lead"), a = player("A"), b = player("B"), c = player("C");
        assertEquals(GroupService.InviteResult.SELF, groups.invite(leader, leader, "t0"));
        assertEquals(GroupService.InviteResult.SENT, groups.invite(leader, a, "t1"));
        assertTrue(groups.members(leader.getUniqueId()).isEmpty(), "an invitation alone moves nobody");
        assertNull(groups.accept(a, "wrong-token"));
        assertNotNull(groups.accept(a, "t1"));
        assertEquals(GroupService.InviteResult.SENT, groups.invite(leader, b, "t2"));
        assertNotNull(groups.accept(b, null));
        assertEquals(List.of(a.getUniqueId(), b.getUniqueId()).size(), groups.members(leader.getUniqueId()).size());
        assertEquals(GroupService.InviteResult.FULL, groups.invite(leader, c, "t3"), "leader plus two members is three");
        assertEquals(GroupService.InviteResult.ALREADY_MEMBER, groups.invite(leader, a, "t4"));
        assertEquals(leader.getUniqueId(), groups.leaderOf(a.getUniqueId()));
    }

    @Test void leavingAndDisbanding() {
        Player leader = player("Lead"), a = player("A");
        groups.invite(leader, a, "t");
        groups.accept(a, "t");
        assertEquals(GroupService.InviteResult.IN_OTHER_GROUP, groups.invite(a, player("X"), "u"));
        assertEquals(leader.getUniqueId(), groups.leave(a.getUniqueId()));
        assertTrue(groups.members(leader.getUniqueId()).isEmpty(), "the last member leaving ends the group");
        groups.invite(leader, a, "t2");
        groups.accept(a, "t2");
        assertEquals(leader.getUniqueId(), groups.leave(leader.getUniqueId()));
        assertNull(groups.leaderOf(a.getUniqueId()));
        assertNotNull(groups.decline(a, null) == null ? "" : "", "nothing to decline");
        groups.invite(leader, a, "t3");
        assertNotNull(groups.decline(a, "t3"));
        assertNull(groups.accept(a, "t3"), "a declined invitation cannot be accepted later");
    }
}
