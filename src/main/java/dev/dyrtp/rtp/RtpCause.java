package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;

public enum RtpCause {
    COMMAND,
    FIRST_JOIN,
    DEATH,
    FORCE,
    /** Walking into an RTP portal region; cooldown and price apply as for /rtp. */
    PORTAL;

    public boolean ignoreCooldown(DyRTP plugin) {
        return switch (this) {
            case FIRST_JOIN -> plugin.getConfig().getBoolean("first-join-rtp.ignore-cooldown", true);
            case DEATH -> plugin.getConfig().getBoolean("death-rtp.ignore-cooldown", true);
            case FORCE -> true;
            case COMMAND, PORTAL -> false;
        };
    }

    public boolean ignoreEconomy(DyRTP plugin) {
        return switch (this) {
            case FIRST_JOIN -> plugin.getConfig().getBoolean("first-join-rtp.ignore-economy", true);
            case DEATH -> plugin.getConfig().getBoolean("death-rtp.ignore-economy", true);
            case FORCE -> true;
            case COMMAND, PORTAL -> false;
        };
    }
}
