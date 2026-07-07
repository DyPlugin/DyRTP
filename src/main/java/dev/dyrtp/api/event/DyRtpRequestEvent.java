package dev.dyrtp.api.event;

import dev.dyrtp.rtp.RtpCause;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DyRtpRequestEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RtpCause cause;
    private World world;
    private boolean cancelled;

    public DyRtpRequestEvent(Player player, RtpCause cause, World world) {
        this.player = player;
        this.cause = cause;
        this.world = world;
    }

    public Player getPlayer() {
        return player;
    }

    public RtpCause getCause() {
        return cause;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
