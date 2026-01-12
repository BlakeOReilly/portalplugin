// BlastSavedBlock.java
package com.blake.portalplugin;

public final class BlastSavedBlock {

    public final int dx;
    public final int dy;
    public final int dz;
    public final String material;

    public BlastSavedBlock(int dx, int dy, int dz, String material) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.material = (material == null || material.isBlank()) ? "AIR" : material;
    }

    public String serialize() {
        return dx + "," + dy + "," + dz + "," + material;
    }

    public static BlastSavedBlock deserialize(String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.split(",");
        if (p.length < 4) return null;
        try {
            int dx = Integer.parseInt(p[0].trim());
            int dy = Integer.parseInt(p[1].trim());
            int dz = Integer.parseInt(p[2].trim());
            String mat = p[3].trim();
            return new BlastSavedBlock(dx, dy, dz, mat);
        } catch (Exception ignored) {
            return null;
        }
    }
}
