/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.File;

/**
 *
 * @author antony
 */
public final class Chunk implements Comparable<Chunk> {
    private final File world;
    private final File file;
    private final ChunkOffset offset;
    private final transient int regionX;
    private final transient int regionZ;
    private final transient String relativePath;

    public Chunk(File world, File file, ChunkOffset offset, int regionX, int regionZ) {
        this.world = world;
        this.file = file;
        this.offset = offset;
        this.regionX = regionX;
        this.regionZ = regionZ;
        StringBuilder tmp = new StringBuilder();
        getRelativePath(tmp, world, file);
        this.relativePath = tmp.toString();
    }

    public File getFile() {
        return file;
    }

    public File getWorld() {
        return world;
    }

    public ChunkOffset getOffset() {
        return offset;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public int getX() {
        return regionX * 32 + (offset == null ? 0 : offset.getX());
    }

    public int getZ() {
        return regionZ * 32 + (offset == null ? 0 : offset.getZ());
    }

    @Override
    public String toString() {
        return "Chunk{" + "world=" + world + ", file=" + file + ", offset=" + offset + '}';
    }

    @Override
    public int compareTo(Chunk o) {
        int ret = file.compareTo(o.file);
        if (ret != 0) return ret;
        if (offset == o.offset) return 0;
        if (offset == null) return 1;
        if (o.offset == null) return -1;
        return offset.getOffset() - o.offset.getOffset();
    }

    private static void getRelativePath(StringBuilder result, File parent, File child) {
        File file = child.getParentFile();
        if (file == null) {
            throw new IllegalArgumentException();
        }
        if (file.equals(parent)) {
            result.append(child.getName());
            return;
        }
        getRelativePath(result, parent, file);
        result.append(File.separatorChar);
        result.append(child.getName());
    }
}
