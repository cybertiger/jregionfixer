/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.cyberiantiger.minecraft.nbt.Tag;
import org.cyberiantiger.minecraft.nbt.TagInputStream;
import org.cyberiantiger.minecraft.nbt.TagOutputStream;
import org.cyberiantiger.minecraft.nbt.TagTuple;

/**
 *
 * @author antony
 */
public class RegionFile implements Closeable {
    public static final Pattern REGION_FILE = Pattern.compile("r.(-?[0-9]+).(-?[0-9]+).mca");
    private static final int BLOCK_SIZE = 4096;
    private final File file;
    private boolean readOnly;
    private final RandomAccessFile fd;
    private final byte[] headerArray;
    private final IntBuffer header;
    private final int x;
    private final int z;
    private final List<ChunkOffset> fileMap = new ArrayList<ChunkOffset>();
    private final Map<ChunkOffset, ChunkStatus> chunkStatus = new HashMap<ChunkOffset, ChunkStatus>();
    private boolean dirty = false;
    private boolean headersLoaded = false;

    private enum ChunkStatus {
        INVALID,
        OK,
        NOT_PRESENT
    }

    public RegionFile(File file, boolean readOnly) throws IOException {
        this.file = file;
        this.fd = new RandomAccessFile(file, readOnly ?  "r" : "rw");
        this.headerArray = new byte[8096];
        this.header = ByteBuffer.wrap(headerArray).asIntBuffer();
        Matcher m = REGION_FILE.matcher(file.getName());
        if (!m.matches()) {
            throw new IOException("Invalid region filename: " + file.getName());
        }
        try {
            x = Integer.parseInt(m.group(1));
            z = Integer.parseInt(m.group(2));
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid region filename: " + file.getName());
        }
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public void loadHeaders() throws IOException {
        long length = fd.length();
        int totalBlockLength = (int)(((fd.length()-1)/BLOCK_SIZE) + 1);
        if (length < BLOCK_SIZE * 2) {
            throw new IOException("File truncated, missing header");
        }
        fd.seek(0);
        fd.readFully(headerArray);
        // Initialize our block map.
        fileMap.clear();
        for (int i = 0; i < totalBlockLength; i++) {
            fileMap.add(null);
        }
        // Load chunks into block map.
        // Mark chunks invalid which point to blocks which do not exist
        // or blocks which are shared between more than 1 chunk.
        for (int i = 0; i < 1024; i++) {
            ChunkOffset offset = new ChunkOffset(i);
            int location = header.get(i);
            if (location == 0) {
                chunkStatus.put(offset, ChunkStatus.NOT_PRESENT);
                continue;
            }
            int blockOffset = (location>>8) & 0xffffff;
            int blockLength = location & 0xff;
            if (blockOffset < 2) {
                chunkStatus.put(offset, ChunkStatus.INVALID);
                continue;
            }
            boolean ok = true;
            for (int j = 0; j < blockLength; j++) {
                if (blockOffset + j  >= fileMap.size()) {
                    ok = false;
                    break;
                }
                ChunkOffset mapped = fileMap.set(blockOffset + j, offset);
                if (mapped != null) {
                    chunkStatus.put(mapped, ChunkStatus.INVALID);
                    ok = false;
                }
            }
            chunkStatus.put(offset, ok ? ChunkStatus.OK : ChunkStatus.INVALID);
        }
        // Remove any invalid chunks from our fileMap.
        for (int i = 0; i < fileMap.size(); i++) {
            ChunkOffset offset = fileMap.get(i);
            if (offset != null) {
                if (chunkStatus.get(offset) != ChunkStatus.OK) {
                    fileMap.set(i, null);
                }
            }
        }
        headersLoaded = true;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isHeadersLoaded() {
        return headersLoaded;
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void close() throws IOException {
        if (dirty) {
            fd.seek(0);
            fd.write(headerArray);
        }
        fd.close();
    }

    public ChunkStatus getChunkStatus(ChunkOffset offset) {
        return chunkStatus.get(offset);
    }

    public void deleteChunk(ChunkOffset offset) throws IOException {
        if (isReadOnly()) throw new IOException("Readonly");
        dirty = true;
        header.put(offset.getOffset(), 0);
        header.put(1024 + offset.getOffset(), 0);
        chunkStatus.put(offset, ChunkStatus.NOT_PRESENT);
        for (int i = 0; i < fileMap.size(); i++) {
            if (offset.equals(fileMap.get(i))) {
                fileMap.set(i, null);
            }
        }
    }

    public void truncate() throws IOException {
        if (isReadOnly()) throw new IOException("Readonly");
        int i = fileMap.size();
        while (--i >= 2) {
            if (fileMap.get(i) == null) {
                fileMap.remove(i);
            } else {
                break;
            }
        }
        if (fd.length() > fileMap.size() * BLOCK_SIZE) {
            fd.setLength(fileMap.size() * BLOCK_SIZE);
        }
    }

    public void writeGzipChunk(ChunkOffset offset, Tag tag) throws IOException {
        if (!isHeadersLoaded()) throw new IllegalStateException();
        if (isReadOnly()) throw new IOException("Readonly");
        dirty = true;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TagOutputStream tagOut = new TagOutputStream(new DeflaterOutputStream(out));
        try {
            tagOut.writeTag(new TagTuple<Tag>("", tag));
        } finally {
            tagOut.close();
        }
        byte[] data = out.toByteArray();
        writeChunkData(offset, data, (byte)1);
    }

    public void writeDeflateChunk(ChunkOffset offset, Tag tag, int deflateType) throws IOException {
        if (!isHeadersLoaded()) throw new IllegalStateException();
        if (isReadOnly()) throw new IOException("Readonly");
        dirty = true;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TagOutputStream tagOut = new TagOutputStream(new DeflaterOutputStream(out));
        try {
            tagOut.writeTag(new TagTuple<Tag>("", tag));
        } finally {
            tagOut.close();
        }
        byte[] data = out.toByteArray();
        writeChunkData(offset, data, (byte)2);
    }

    private void writeChunkData(ChunkOffset offset, byte[] data, byte compression) throws IOException {
        long fileOffset = 0L;
        int dataLength = data.length + 5; // Length of data including header.
        int blockLength = ((dataLength-1)>>12) + 1; // Length of data in 4k blocks.
        // Attempt to reuse existing offset.
        if (chunkStatus.get(offset) == ChunkStatus.OK) {
            int location = header.get(offset.getOffset());
            if (blockLength <= (location & 0xff)) {
                fileOffset = (location & 0xFFFFFF00L)<<4;
            }
        }
        // (Re)allocation file location to save chunk.
        if (fileOffset == 0L) {
            int location = allocateChunk(offset, ((data.length+4)>>12) + 1 );
            fileOffset = (location & 0xFFFFFF00L)<<4;
            header.put(offset.getOffset(), location);
        }
        header.put(offset.getOffset()+1024, (int)System.currentTimeMillis() / 1000); // Y2k38 bug.
        fd.seek(fileOffset);
        fd.writeInt(data.length);
        fd.write(compression);
        fd.write(data);
        chunkStatus.put(offset, ChunkStatus.OK);
    }

    public Tag readChunk(ChunkOffset offset) throws IOException {
        if (!isHeadersLoaded()) throw new IllegalStateException();
        ChunkStatus status = getChunkStatus(offset);
        switch (status) {
            case INVALID:
                throw new CorruptChunkException("Chunk entry in header was invalid");
            case NOT_PRESENT:
                return null;
        }
        int location = header.get(offset.getOffset());
        long fileOffset = (location & 0xFFFFFF00L)<<4;
        int chunkLength = (location & 0xFF) << 12;
        if (fileOffset + 5 >= fd.length()) {
            throw new CorruptChunkException("Chunk data is located beyond the end of the file.");
        }
        fd.seek(fileOffset);
        int actualLength = fd.readInt();
        int compression = fd.read();
        if (actualLength + 5 > chunkLength) {
            throw new CorruptChunkException("Chunk length exceeds file length in header");
        }
        if (fd.getFilePointer() + actualLength > fd.length()) {
            throw new CorruptChunkException("Chunk data is located beyond the end of file.");
        }
        TagInputStream in;
        switch (compression) {
            case 1:
                in = new TagInputStream(new GZIPInputStream(new BufferedInputStream(new RandomAccessFileInputStream(fd, actualLength))));
                break;
            case 2:
                in = new TagInputStream(new InflaterInputStream(new BufferedInputStream(new RandomAccessFileInputStream(fd, actualLength))));
                break;
            default:
                throw new CorruptChunkException("Chunk data contains invalid compression type: " + compression);
        }
        return in.readTag().getValue();
    }

    private int allocateChunk(ChunkOffset chunkOffset, int size) {
        int freeCount = 0;
        if (chunkStatus.get(chunkOffset) == ChunkStatus.OK) {
            int location = header.get(chunkOffset.getOffset());
            int offset = (location >> 8) & 0xffffff;
            int maxOffset = offset + (location & 0xff);
            for (int i = offset; i < maxOffset; i++) {
                fileMap.set(i, null);
            }
        }
        for (int i = 2; i < fileMap.size(); i++) {
            ChunkOffset offset = fileMap.get(i);
            if (offset == null) {
                freeCount++;
            } else {
                freeCount = 0;
            }
            if (freeCount >= size) {
                for (int j = 1; j <= freeCount; j++) {
                    fileMap.set(i-j, chunkOffset);
                }
                return ((i-freeCount) << 8) | size;
            }
        }
        for (int i = 1; i <= freeCount; i++) {
            fileMap.set(fileMap.size() - i, chunkOffset);
        }
        while (freeCount < size) {
            freeCount++;
            fileMap.add(chunkOffset);
        }
        return ((fileMap.size() - freeCount)<<8) | size;
    }

}