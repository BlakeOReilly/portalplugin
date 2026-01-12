package com.blake.portalplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

public final class BlastSchematicPaster {

    private BlastSchematicPaster() {}

    public static final class PasteResult {
        public final boolean success;
        public final String message;

        public PasteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static PasteResult paste(Plugin plugin, File schematicFile, Location pasteAt, boolean ignoreAir, boolean copyEntities) {
        try {
            if (pasteAt == null || pasteAt.getWorld() == null) {
                return new PasteResult(false, "Paste location/world is missing.");
            }

            if (schematicFile == null || !schematicFile.exists()) {
                return new PasteResult(false, "Schematic file not found: " + (schematicFile == null ? "null" : schematicFile.getPath()));
            }

            // Runtime requirement (not compile-time): WorldEdit or FAWE installed
            var we = Bukkit.getPluginManager().getPlugin("WorldEdit");
            var fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
            if ((we == null || !we.isEnabled()) && (fawe == null || !fawe.isEnabled())) {
                return new PasteResult(false, "WorldEdit/FAWE is not installed or not enabled on this server.");
            }

            // ---------- WorldEdit reflection ----------
            Class<?> clipboardFormatsClz = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
            Method findByFile = clipboardFormatsClz.getMethod("findByFile", File.class);
            Object format = findByFile.invoke(null, schematicFile);
            if (format == null) {
                return new PasteResult(false, "Unsupported schematic format for: " + schematicFile.getName());
            }

            Object clipboard;
            try (InputStream in = new FileInputStream(schematicFile)) {
                Method getReader = format.getClass().getMethod("getReader", InputStream.class);
                Object reader = getReader.invoke(format, in);
                Method read = reader.getClass().getMethod("read");
                clipboard = read.invoke(reader);
            }

            // Bukkit world -> WorldEdit world
            Class<?> bukkitAdapterClz = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptWorld = bukkitAdapterClz.getMethod("adapt", org.bukkit.World.class);
            Object weWorld = adaptWorld.invoke(null, pasteAt.getWorld());

            // Create edit session: WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()
            Class<?> worldEditClz = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object weInstance = worldEditClz.getMethod("getInstance").invoke(null);
            Object builder = weInstance.getClass().getMethod("newEditSessionBuilder").invoke(weInstance);

            Class<?> weWorldClz = Class.forName("com.sk89q.worldedit.world.World");
            builder.getClass().getMethod("world", weWorldClz).invoke(builder, weWorld);
            Object editSession = builder.getClass().getMethod("build").invoke(builder);

            // Build paste operation
            Class<?> clipboardClz = Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard");
            Class<?> holderClz = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
            Object holder = holderClz.getConstructor(clipboardClz).newInstance(clipboard);

            Class<?> editSessionClz = Class.forName("com.sk89q.worldedit.EditSession");
            Object pasteBuilder = holder.getClass().getMethod("createPaste", editSessionClz).invoke(holder, editSession);

            Class<?> blockVector3Clz = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object toVec = blockVector3Clz.getMethod("at", int.class, int.class, int.class)
                    .invoke(null, pasteAt.getBlockX(), pasteAt.getBlockY(), pasteAt.getBlockZ());

            pasteBuilder.getClass().getMethod("to", blockVector3Clz).invoke(pasteBuilder, toVec);

            // ignoreAirBlocks(boolean) if present
            tryInvokeBoolean(pasteBuilder, "ignoreAirBlocks", ignoreAir);

            // copyEntities(boolean) if present
            tryInvokeBoolean(pasteBuilder, "copyEntities", copyEntities);

            Object operation = pasteBuilder.getClass().getMethod("build").invoke(pasteBuilder);

            Class<?> operationClz = Class.forName("com.sk89q.worldedit.function.operation.Operation");
            Class<?> operationsClz = Class.forName("com.sk89q.worldedit.function.operation.Operations");
            operationsClz.getMethod("complete", operationClz).invoke(null, operation);

            // Close edit session (try close(), then try flushQueue() fallback)
            try {
                editSession.getClass().getMethod("close").invoke(editSession);
            } catch (Throwable ignored) {
                try {
                    editSession.getClass().getMethod("flushQueue").invoke(editSession);
                } catch (Throwable ignored2) {}
            }

            return new PasteResult(true, "Pasted schematic: " + schematicFile.getName());

        } catch (Throwable t) {
            if (plugin != null) plugin.getLogger().warning("[BLAST] Schematic paste failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return new PasteResult(false, "Paste failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void tryInvokeBoolean(Object target, String methodName, boolean value) {
        if (target == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, boolean.class);
            m.invoke(target, value);
        } catch (Throwable ignored) {}
    }
}
