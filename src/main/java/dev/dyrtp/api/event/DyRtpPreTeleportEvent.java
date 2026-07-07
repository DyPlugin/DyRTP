package dev.dyrtp.api.event;

import dev.dyrtp.rtp.RtpCause;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DyRtpPreTeleportEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RtpCause cause;
    private Location location;
    private boolean cancelled;

    public DyRtpPreTeleportEvent(Player player, RtpCause cause, Location location) {
        this.player = player;
        this.cause = cause;
        this.location = location;
    }

    public Player getPlayer() {
        return player;
    }

    public RtpCause getCause() {
        return cause;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
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
