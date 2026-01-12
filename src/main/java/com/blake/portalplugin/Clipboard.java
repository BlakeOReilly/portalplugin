package com.blake.portalplugin.worldedit;

public class Clipboard {

    private final int width;
    private final int height;
    private final int length;
    private final String[] blockDataStrings; // index = x + width*(y + height*z)

    public Clipboard(int width, int height, int length, String[] blockDataStrings) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.length = Math.max(1, length);
        this.blockDataStrings = blockDataStrings;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLength() { return length; }

    public long getVolume() {
        return (long) width * (long) height * (long) length;
    }

    public String[] getRawData() { return blockDataStrings; }

    public int index(int x, int y, int z) {
        return x + width * (y + height * z);
    }

    public String get(int x, int y, int z) {
        return blockDataStrings[index(x, y, z)];
    }

    public void set(int x, int y, int z, String dataString) {
        blockDataStrings[index(x, y, z)] = dataString;
    }
}
