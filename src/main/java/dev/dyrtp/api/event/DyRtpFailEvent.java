package dev.dyrtp.api.event;

import dev.dyrtp.rtp.RtpCause;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DyRtpFailEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RtpCause cause;
    private final int attempts;

    public DyRtpFailEvent(Player player, RtpCause cause, int attempts) {
        this.player = player;
        this.cause = cause;
        this.attempts = attempts;
    }

    public Player getPlayer() {
        return player;
    }

    public RtpCause getCause() {
        return cause;
    }

    public int getAttempts() {
        return attempts;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
