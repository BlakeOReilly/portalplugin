package com.blake.portalplugin;

import java.util.Locale;

public enum BlastPowerupType {
    SPEED("speed", "Speed powerup"),
    JUMP("jump", "Jump powerup"),
    BLAST_SPEED("blast_speed", "Blast speed powerup"),
    BLASTER_DAMAGE("blaster_damage", "Blaster Damage Powerup"),
    DASH("dash", "Dash powerup"),
    KNOCKBACK("knockback", "Knockback powerup"),
    SLOW_SHOT("slow_shot", "Slow Shot powerup"),
    BLIND_SHOT("blind_shot", "Blind Shot powerup"),
    MARK_TARGET("mark_target", "Mark Target powerup"),
    CONFUSION("confusion", "Confusion powerup");

    private final String key;
    private final String display;

    BlastPowerupType(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String getKey() {
        return key;
    }

    public String getDisplay() {
        return display;
    }

    public static BlastPowerupType fromKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (BlastPowerupType t : values()) {
            if (t.key.equalsIgnoreCase(k)) return t;
        }
        return null;
    }
}
