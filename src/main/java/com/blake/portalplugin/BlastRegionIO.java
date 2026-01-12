package com.blake.portalplugin;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class BlastRegionIO {

    private BlastRegionIO() {}

    private static final int VERSION = 1;

    public static void save(File file, BlastRegionSnapshot snap) throws IOException {
        if (file == null || snap == null) throw new IOException("Missing file or snapshot");

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(file);
             GZIPOutputStream gz = new GZIPOutputStream(fos);
             DataOutputStream out = new DataOutputStream(gz)) {

            out.writeInt(0x42525331); // "BRS1"
            out.writeInt(VERSION);

            out.writeInt(snap.getSizeX());
            out.writeInt(snap.getSizeY());
            out.writeInt(snap.getSizeZ());

            out.writeInt(snap.getPalette().size());
            for (String s : snap.getPalette()) {
                out.writeUTF(s == null ? "" : s);
            }

            int[] data = snap.getData();
            out.writeInt(data.length);
            for (int v : data) {
                out.writeInt(v);
            }
        }
    }

    public static BlastRegionSnapshot load(File file) throws IOException {
        if (file == null || !file.exists()) throw new FileNotFoundException("Snapshot not found: " + file);

        try (FileInputStream fis = new FileInputStream(file);
             GZIPInputStream gz = new GZIPInputStream(fis);
             DataInputStream in = new DataInputStream(gz)) {

            int magic = in.readInt();
            if (magic != 0x42525331) { // "BRS1"
                throw new IOException("Invalid snapshot magic");
            }

            int ver = in.readInt();
            if (ver != VERSION) {
                throw new IOException("Unsupported snapshot version: " + ver);
            }

            int sizeX = in.readInt();
            int sizeY = in.readInt();
            int sizeZ = in.readInt();

            int paletteSize = in.readInt();
            BlastRegionSnapshot snap = new BlastRegionSnapshot(sizeX, sizeY, sizeZ, new int[Math.max(0, sizeX * sizeY * sizeZ)]);
            snap.getPalette().clear();

            for (int i = 0; i < paletteSize; i++) {
                snap.getPalette().add(in.readUTF());
            }

            int dataLen = in.readInt();
            int[] data = new int[dataLen];
            for (int i = 0; i < dataLen; i++) {
                data[i] = in.readInt();
            }

            return new BlastRegionSnapshot(sizeX, sizeY, sizeZ, data) {{
                getPalette().clear();
                getPalette().addAll(snap.getPalette());
            }};
        }
    }
}
