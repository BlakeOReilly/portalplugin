// src/main/java/com/blake/portalplugin/worldedit/ClipboardTransforms.java
package com.blake.portalplugin.worldedit;

import com.blake.portalplugin.worldedit.ClipboardManager.FlipAxis;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;

import java.util.EnumSet;

public final class ClipboardTransforms {

    private ClipboardTransforms() {}

    public static Clipboard rotateY(Clipboard in, int degClockwise) {
        int w = in.getWidth();
        int h = in.getHeight();
        int l = in.getLength();

        int deg = ((degClockwise % 360) + 360) % 360;
        if (deg != 90 && deg != 180 && deg != 270) deg = 90;

        int outW = (deg == 180) ? w : l;
        int outL = (deg == 180) ? l : w;

        String[] outData = new String[outW * h * outL];
        Clipboard out = new Clipboard(outW, h, outL, outData);

        for (int z = 0; z < l; z++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {

                    String s = in.get(x, y, z);
                    String transformed = transformBlockDataStringRotate(s, deg);

                    int nx, nz;
                    if (deg == 90) {
                        nx = z;
                        nz = (w - 1 - x);
                    } else if (deg == 180) {
                        nx = (w - 1 - x);
                        nz = (l - 1 - z);
                    } else { // 270
                        nx = (l - 1 - z);
                        nz = x;
                    }

                    out.set(nx, y, nz, transformed);
                }
            }
        }

        return out;
    }

    public static Clipboard flip(Clipboard in, FlipAxis axis) {
        int w = in.getWidth();
        int h = in.getHeight();
        int l = in.getLength();

        String[] outData = new String[w * h * l];
        Clipboard out = new Clipboard(w, h, l, outData);

        for (int z = 0; z < l; z++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {

                    String s = in.get(x, y, z);
                    String transformed = transformBlockDataStringFlip(s, axis);

                    int nx = x;
                    int nz = z;

                    if (axis == FlipAxis.X) {
                        nx = (w - 1 - x);
                    } else if (axis == FlipAxis.Z) {
                        nz = (l - 1 - z);
                    }

                    out.set(nx, y, nz, transformed);
                }
            }
        }

        return out;
    }

    private static String transformBlockDataStringRotate(String dataString, int deg) {
        if (dataString == null || dataString.isBlank()) return dataString;
        try {
            BlockData bd = Bukkit.createBlockData(dataString);
            rotateBlockDataInPlace(bd, deg);
            return bd.getAsString();
        } catch (Throwable ignored) {
            return dataString;
        }
    }

    private static String transformBlockDataStringFlip(String dataString, FlipAxis axis) {
        if (dataString == null || dataString.isBlank()) return dataString;
        try {
            BlockData bd = Bukkit.createBlockData(dataString);
            flipBlockDataInPlace(bd, axis);
            return bd.getAsString();
        } catch (Throwable ignored) {
            return dataString;
        }
    }

    private static void rotateBlockDataInPlace(BlockData bd, int deg) {

        // Directional facing (stairs, doors, trapdoors, etc.)
        if (bd instanceof Directional d) {
            d.setFacing(rotateFace(d.getFacing(), deg));
        }

        // Rotatable rotation (skulls, item frames, etc.)
        if (bd instanceof Rotatable r) {
            r.setRotation(rotateFace(r.getRotation(), deg));
        }

        // Axis-based blocks (logs, pillars)
        if (bd instanceof Orientable o) {
            if (deg == 90 || deg == 270) {
                Axis ax = o.getAxis();
                if (ax == Axis.X) o.setAxis(Axis.Z);
                else if (ax == Axis.Z) o.setAxis(Axis.X);
            }
        }

        // MultipleFacing (fences, panes, etc.)
        if (bd instanceof MultipleFacing mf) {
            EnumSet<BlockFace> horizontals = EnumSet.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
            boolean n = mf.hasFace(BlockFace.NORTH);
            boolean e = mf.hasFace(BlockFace.EAST);
            boolean s = mf.hasFace(BlockFace.SOUTH);
            boolean w = mf.hasFace(BlockFace.WEST);

            for (BlockFace f : horizontals) mf.setFace(f, false);

            if (n) mf.setFace(rotateFace(BlockFace.NORTH, deg), true);
            if (e) mf.setFace(rotateFace(BlockFace.EAST, deg), true);
            if (s) mf.setFace(rotateFace(BlockFace.SOUTH, deg), true);
            if (w) mf.setFace(rotateFace(BlockFace.WEST, deg), true);
        }

        // Rails shapes
        if (bd instanceof Rail rail) {
            rail.setShape(rotateRailShape(rail.getShape(), deg));
        }
    }

    private static void flipBlockDataInPlace(BlockData bd, FlipAxis axis) {

        if (bd instanceof Directional d) {
            d.setFacing(flipFace(d.getFacing(), axis));
        }

        if (bd instanceof Rotatable r) {
            r.setRotation(flipFace(r.getRotation(), axis));
        }

        if (bd instanceof MultipleFacing mf) {
            EnumSet<BlockFace> horizontals = EnumSet.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
            boolean n = mf.hasFace(BlockFace.NORTH);
            boolean e = mf.hasFace(BlockFace.EAST);
            boolean s = mf.hasFace(BlockFace.SOUTH);
            boolean w = mf.hasFace(BlockFace.WEST);

            for (BlockFace f : horizontals) mf.setFace(f, false);

            if (n) mf.setFace(flipFace(BlockFace.NORTH, axis), true);
            if (e) mf.setFace(flipFace(BlockFace.EAST, axis), true);
            if (s) mf.setFace(flipFace(BlockFace.SOUTH, axis), true);
            if (w) mf.setFace(flipFace(BlockFace.WEST, axis), true);
        }

        if (bd instanceof Rail rail) {
            rail.setShape(flipRailShape(rail.getShape(), axis));
        }
    }

    private static BlockFace rotateFace(BlockFace f, int deg) {
        if (f == null) return null;

        int times = (deg == 90) ? 1 : (deg == 180) ? 2 : 3;
        BlockFace out = f;

        for (int i = 0; i < times; i++) {
            out = switch (out) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;

                case NORTH_EAST -> BlockFace.SOUTH_EAST;
                case SOUTH_EAST -> BlockFace.SOUTH_WEST;
                case SOUTH_WEST -> BlockFace.NORTH_WEST;
                case NORTH_WEST -> BlockFace.NORTH_EAST;

                default -> out; // UP/DOWN and others unchanged
            };
        }

        return out;
    }

    private static BlockFace flipFace(BlockFace f, FlipAxis axis) {
        if (f == null) return null;

        if (axis == FlipAxis.X) {
            return switch (f) {
                case EAST -> BlockFace.WEST;
                case WEST -> BlockFace.EAST;
                case NORTH_EAST -> BlockFace.NORTH_WEST;
                case NORTH_WEST -> BlockFace.NORTH_EAST;
                case SOUTH_EAST -> BlockFace.SOUTH_WEST;
                case SOUTH_WEST -> BlockFace.SOUTH_EAST;
                default -> f;
            };
        } else {
            return switch (f) {
                case NORTH -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.NORTH;
                case NORTH_EAST -> BlockFace.SOUTH_EAST;
                case SOUTH_EAST -> BlockFace.NORTH_EAST;
                case NORTH_WEST -> BlockFace.SOUTH_WEST;
                case SOUTH_WEST -> BlockFace.NORTH_WEST;
                default -> f;
            };
        }
    }

    private static Rail.Shape rotateRailShape(Rail.Shape s, int deg) {
        if (s == null) return null;

        int times = (deg == 90) ? 1 : (deg == 180) ? 2 : 3;
        Rail.Shape out = s;

        for (int i = 0; i < times; i++) {
            out = switch (out) {
                case NORTH_SOUTH -> Rail.Shape.EAST_WEST;
                case EAST_WEST -> Rail.Shape.NORTH_SOUTH;

                case ASCENDING_NORTH -> Rail.Shape.ASCENDING_EAST;
                case ASCENDING_EAST -> Rail.Shape.ASCENDING_SOUTH;
                case ASCENDING_SOUTH -> Rail.Shape.ASCENDING_WEST;
                case ASCENDING_WEST -> Rail.Shape.ASCENDING_NORTH;

                case NORTH_EAST -> Rail.Shape.SOUTH_EAST;
                case SOUTH_EAST -> Rail.Shape.SOUTH_WEST;
                case SOUTH_WEST -> Rail.Shape.NORTH_WEST;
                case NORTH_WEST -> Rail.Shape.NORTH_EAST;

                default -> out;
            };
        }

        return out;
    }

    private static Rail.Shape flipRailShape(Rail.Shape s, FlipAxis axis) {
        if (s == null) return null;

        if (axis == FlipAxis.X) {
            return switch (s) {
                case ASCENDING_EAST -> Rail.Shape.ASCENDING_WEST;
                case ASCENDING_WEST -> Rail.Shape.ASCENDING_EAST;

                case NORTH_EAST -> Rail.Shape.NORTH_WEST;
                case NORTH_WEST -> Rail.Shape.NORTH_EAST;
                case SOUTH_EAST -> Rail.Shape.SOUTH_WEST;
                case SOUTH_WEST -> Rail.Shape.SOUTH_EAST;

                default -> s;
            };
        } else {
            return switch (s) {
                case ASCENDING_NORTH -> Rail.Shape.ASCENDING_SOUTH;
                case ASCENDING_SOUTH -> Rail.Shape.ASCENDING_NORTH;

                case NORTH_EAST -> Rail.Shape.SOUTH_EAST;
                case SOUTH_EAST -> Rail.Shape.NORTH_EAST;
                case NORTH_WEST -> Rail.Shape.SOUTH_WEST;
                case SOUTH_WEST -> Rail.Shape.NORTH_WEST;

                default -> s;
            };
        }
    }
}
