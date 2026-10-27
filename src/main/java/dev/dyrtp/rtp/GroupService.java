package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RTP groups: a leader invites players, those who accept travel with the leader's next RTP to the same spot.
 * Groups and invitations live in memory and expire; nobody is ever moved without having accepted.
 */
public class GroupService {
    /** An invitation waiting for an answer; the token stops an old button from joining a newer group. */
    public record Invite(UUID leader, String leaderName, String token, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final DyRTP plugin;
    private final Map<UUID, Set<UUID>> groups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> groupExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> memberOf = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public GroupService(DyRTP plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("group.enabled", true);
    }

    public int maxSize() {
        return Math.max(2, Math.min(16, plugin.getConfig().getInt("group.max-size", 4)));
    }

    private long lifetimeMillis() {
        return Math.max(30, plugin.getConfig().getLong("group.expire-seconds", 300)) * 1000L;
    }

    /** Members of the player's group when they lead one, without the leader. Expired groups are dropped. */
    public List<UUID> members(UUID leader) {
        Long expires = groupExpiry.get(leader);
        if (expires == null) return List.of();
        if (System.currentTimeMillis() > expires) {
            disband(leader);
            return List.of();
        }
        Set<UUID> members = groups.get(leader);
        return members == null ? List.of() : List.copyOf(members);
    }

    /** The leader of the group a player joined, or null. */
    public UUID leaderOf(UUID member) {
        UUID leader = memberOf.get(member);
        if (leader != null && members(leader).isEmpty()) return null;
        return leader;
    }

    public enum InviteResult { SENT, SELF, FULL, ALREADY_MEMBER, IN_OTHER_GROUP }

    public InviteResult invite(Player leader, Player target, String token) {
        if (leader.getUniqueId().equals(target.getUniqueId())) return InviteResult.SELF;
        if (memberOf.containsKey(leader.getUniqueId()) && leaderOf(leader.getUniqueId()) != null) return InviteResult.IN_OTHER_GROUP;
        List<UUID> current = members(leader.getUniqueId());
        if (current.contains(target.getUniqueId())) return InviteResult.ALREADY_MEMBER;
        if (current.size() + 2 > maxSize()) return InviteResult.FULL; // The leader counts too.
        invites.put(target.getUniqueId(), new Invite(leader.getUniqueId(), leader.getName(), token,
                System.currentTimeMillis() + Math.max(10, plugin.getConfig().getLong("group.invite-seconds", 60)) * 1000L));
        return InviteResult.SENT;
    }

    /** Accepts the pending invitation; null when there is none, it expired, the token is stale or the group is full. */
    public Invite accept(Player player, String token) {
        Invite invite = invites.get(player.getUniqueId());
        if (invite == null || invite.expired() || (token != null && !invite.token().equals(token))) return null;
        invites.remove(player.getUniqueId(), invite);
        leave(player.getUniqueId());
        Set<UUID> members = groups.computeIfAbsent(invite.leader(), id -> ConcurrentHashMap.newKeySet());
        if (members.size() + 2 > maxSize()) return null;
        members.add(player.getUniqueId());
        memberOf.put(player.getUniqueId(), invite.leader());
        groupExpiry.put(invite.leader(), System.currentTimeMillis() + lifetimeMillis());
        return invite;
    }

    public Invite decline(Player player, String token) {
        Invite invite = invites.get(player.getUniqueId());
        if (invite == null || (token != null && !invite.token().equals(token))) return null;
        invites.remove(player.getUniqueId(), invite);
        return invite;
    }

    /** Leaves the group the player joined; a leader leaving disbands the group. Returns the old leader or null. */
    public UUID leave(UUID player) {
        if (groups.containsKey(player)) {
            disband(player);
            return player;
        }
        UUID leader = memberOf.remove(player);
        if (leader != null) {
            Set<UUID> members = groups.get(leader);
            if (members != null) {
                members.remove(player);
                if (members.isEmpty()) disband(leader);
            }
        }
        return leader;
    }

    public void disband(UUID leader) {
        Set<UUID> members = groups.remove(leader);
        groupExpiry.remove(leader);
        if (members != null) for (UUID member : members) memberOf.remove(member, leader);
    }

    /** After a group RTP the group stays together for the next one; its expiry is renewed. */
    public void used(UUID leader) {
        if (groups.containsKey(leader)) groupExpiry.put(leader, System.currentTimeMillis() + lifetimeMillis());
    }

    public void clear() {
        groups.clear();
        groupExpiry.clear();
        memberOf.clear();
        invites.clear();
    }

    /** Members in invitation order, for messages. */
    public Set<UUID> ordered(UUID leader) {
        return new LinkedHashSet<>(members(leader));
    }
}
