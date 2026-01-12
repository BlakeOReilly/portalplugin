package com.blake.portalplugin;

import java.util.ArrayList;
import java.util.List;

public class BlastRegionSnapshot {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    // palette of BlockData strings
    private final List<String> palette = new ArrayList<>();

    // indices into palette (length = sizeX*sizeY*sizeZ)
    private final int[] data;

    public BlastRegionSnapshot(int sizeX, int sizeY, int sizeZ, int[] data) {
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
        this.data = data;
    }

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }

    public int[] getData() { return data; }

    public List<String> getPalette() { return palette; }

    public int getTotalBlocks() {
        long total = (long) sizeX * (long) sizeY * (long) sizeZ;
        return (total > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) total;
    }
}