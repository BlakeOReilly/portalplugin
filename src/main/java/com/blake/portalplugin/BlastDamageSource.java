package com.blake.portalplugin;

public enum BlastDamageSource {
    DEFAULT(false),
    STRIKE_BLASTER(true),
    HOMING_MISSILE(true);

    private final boolean bypassSpawnProtection;

    BlastDamageSource(boolean bypassSpawnProtection) {
        this.bypassSpawnProtection = bypassSpawnProtection;
    }

    public boolean bypassesSpawnProtection() {
        return bypassSpawnProtection;
    }
}
