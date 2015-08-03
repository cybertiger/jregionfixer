/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

/**
 *
 * @author antony
 */
public class ChunkOffset {
    private int offset;

    public ChunkOffset(int offset) {
        this.offset = offset;
    }
    
    public ChunkOffset(int x, int z) {
        this(x | z<<5);
    }

    public int getX() {
        return offset & 0x1f;
    }

    public int getZ() {
        return (offset>>5) & 0x1f;
    }

    @Override
    public String toString() {
        return "ChunkOffset{" + "x=" + getX() + ", z=" + getZ() + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + this.offset;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ChunkOffset other = (ChunkOffset) obj;
        if (this.offset != other.offset) {
            return false;
        }
        return true;
    }

    public int getOffset() {
        return offset;
    }
}
