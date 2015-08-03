/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.cyberiantiger.minecraft.nbt.CompoundTag;
import org.cyberiantiger.minecraft.nbt.ListTag;
import org.cyberiantiger.minecraft.nbt.Tag;
import org.cyberiantiger.minecraft.nbt.TagInputStream;
import org.cyberiantiger.minecraft.nbt.TagOutputStream;
import org.cyberiantiger.minecraft.nbt.TagType;
import static org.cyberiantiger.minecraft.jregionfixer.ErrorAction.*;

/**
 *
 * @author antony
 */
public class RegionFile {
    public static final Pattern REGION_FILE = Pattern.compile("r.(-?[0-9]+).(-?[0-9]+).mca");
    private static final int BLOCK_SIZE = 4096;
    private final File world;
    private final File file;
    private final ErrorHandler results;
    private final CheckParameters params;
    private final RandomAccessFile fd;
    private final byte[] headerArray;
    private final IntBuffer header;
    private final int x;
    private final int z;
    private final List<ChunkOffset> fileMap = new ArrayList<ChunkOffset>();
    private final Map<ChunkOffset, ChunkStatus> chunkStatus = new HashMap<ChunkOffset, ChunkStatus>();
    private boolean dirty = false;

    private enum ChunkStatus {
        INVALID,
        OK,
        NOT_PRESENT
    }

    public RegionFile(File world, File file, ErrorHandler results, CheckParameters params) throws IOException {
        this.world = world;
        this.file = file;
        this.results = results;
        this.params = params;
        this.fd = new RandomAccessFile(file, params.isReadonly() ?  "r" : "rw");
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
    }

    public void close() throws IOException {
        if (dirty) {
            fd.seek(0);
            fd.write(headerArray);
        }
        fd.close();
    }


    private void checkChunkData() {
NEXT_CHUNK:
        for (int i = 0; i < 1024; i++) {
            int location = header.get(i);
            if (location == 0) {
                continue;
            }

            ChunkOffset chunkOffset = new ChunkOffset(i);
            Chunk chunk = new Chunk(world, file, chunkOffset, x, z);

            long fileOffset = (location & 0xFFFFFF00L)<<4;
            int chunkLength = (location & 0xFF) << 12;

            try {
                if (fileOffset + 5 >= fd.length()) {
                    results.corruptChunk(chunk, "Chunk data is located beyond the end of file.", EnumSet.of(NONE), NONE);
                    continue;
                }
                fd.seek(fileOffset);
                int actualLength = fd.readInt();
                int compression = fd.read();
                if (actualLength + 5 > chunkLength) {
                    results.corruptChunk(chunk, "Chunk length exceeds file length in header", EnumSet.of(NONE), NONE);
                    continue;
                }
                if (fd.getFilePointer() + actualLength > fd.length()) {
                    results.corruptChunk(chunk, "Chunk data is located beyond the end of file.", EnumSet.of(NONE), NONE);
                    continue;
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
                        results.corruptChunk(chunk, "Chunk data contains invalid compression type: " + compression, EnumSet.of(NONE), NONE);
                        continue NEXT_CHUNK;
                }
                try {
                    Tag data = in.readTag();
                    if (data.getType() != TagType.COMPOUND) {
                        results.corruptChunk(chunk, "Chunk data not a compound", EnumSet.of(NONE), NONE);
                        // Fatal, no point performing further checks.
                        continue NEXT_CHUNK;
                    }
                    CompoundTag dataC = (CompoundTag) data;
                    if (!dataC.containsKey("Level", TagType.COMPOUND)) {
                        results.corruptChunk(chunk, "Chunk data missing \"Level\" compound.", EnumSet.of(NONE), NONE);
                        // Fatal, no point performing further checks.
                        continue NEXT_CHUNK;
                    }
                    CompoundTag level = dataC.getCompound("Level");
                    if (!level.containsKey("xPos", TagType.INT)) {
                        results.invalidXPosition(chunk, "xPos tag missing or wrong type.", EnumSet.of(NONE), NONE);
                    }
                    if (level.getInt("xPos") != chunk.getX()) {
                        results.invalidXPosition(chunk, "xPos: " + level.getInt("xPos") + " does not match expected xPos: " + chunk.getX(), EnumSet.of(NONE), NONE);
                    }
                    if (!level.containsKey("zPos", TagType.INT)) {
                        results.invalidZPosition(chunk, "zPos tag missing or wrong type.", EnumSet.of(NONE), NONE);
                    }
                    if (level.getInt("zPos") != chunk.getZ()) {
                        results.invalidZPosition(chunk, "zPos: " + level.getInt("zPos") + " does not match expected zPos: " + z, EnumSet.of(NONE), NONE);
                    }
                    if (!level.containsKey("Entities", TagType.LIST)) {
                        results.invalidEntities(chunk, "Entities tag missing or wrong type.", EnumSet.of(NONE), NONE);
                    }
                    if (params.isCheckEntityCount()) {
                        ListTag entities = level.getList("Entities");
                        if (!entities.isEmpty()) {
                            if (entities.getListType() != TagType.COMPOUND) {
                                results.invalidEntities(chunk, "Entities are wrong type: " + entities.getListType().name(), EnumSet.of(NONE), NONE);
                            } else if (entities.size() > params.getMaxEntities()) {
                                results.tooManyEntities(chunk, "Found " + entities.size() + " entities", EnumSet.of(NONE), NONE);
                            }
                        }
                    }
                    if (!level.containsKey("TileEntities", TagType.LIST)) {
                        results.invalidTileEntities(chunk, "TileEntities tag missing or wrong type.", EnumSet.of(NONE), NONE);
                    } else if (params.isCheckTileEntityCount() || params.isCheckTileEntityLocation()) {
                        ListTag tileEntities = level.getList("TileEntities");
                        if (!tileEntities.isEmpty() && tileEntities.getListType() != TagType.COMPOUND) {
                            results.invalidTileEntities(chunk, "TileEntities are wrong type: " + tileEntities.getListType().name(), EnumSet.of(NONE), NONE);
                        } else {
                            if (params.isCheckTileEntityCount() && tileEntities.size() > params.getMaxTileEntities()) {
                                results.tooManyTileEntities(chunk, "Found " + tileEntities.size() + " tile entities", EnumSet.of(NONE), NONE);
                            }
                            if (params.isCheckTileEntityLocation()) {
                                if (!tileEntities.isEmpty()) {
                                    for (CompoundTag tileEntity : (CompoundTag[]) tileEntities.getValue()) {
                                        if (!tileEntity.containsKey("x", TagType.INT)) {
                                            results.invalidTileEntity(chunk, "Invalid tile entity, no x position", EnumSet.of(NONE), NONE);
                                            break;
                                        }
                                        if (!tileEntity.containsKey("y", TagType.INT)) {
                                            results.invalidTileEntity(chunk, "Invalid tile entity, no y position", EnumSet.of(NONE), NONE);
                                            break;
                                        }
                                        if (!tileEntity.containsKey("z", TagType.INT)) {
                                            results.invalidTileEntity(chunk, "Invalid tile entity, no z position", EnumSet.of(NONE), NONE);
                                            break;
                                        }
                                        int x = tileEntity.getInt("x");
                                        int y = tileEntity.getInt("y");
                                        int z = tileEntity.getInt("z");
                                        int chunkX = x - chunk.getX();
                                        int chunkZ = z - chunk.getZ();
                                        if (chunkX < 0 || chunkX > 15 || chunkZ < 0 || chunkZ > 15 || y < 0 || y > 255) {
                                            results.misplacedTileEntity(chunk, "Invalid tile entity, located outside chunk, relative offset: " + chunkX + ", " + y + ", " + chunkZ, EnumSet.of(NONE), NONE);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                results.corruptChunk(chunk, "Exception parsing tag data: " + e.getClass().getName() + ": " + e.getMessage(), EnumSet.of(NONE), NONE);
            }
        }
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

    public void deleteChunk(ChunkOffset offset) throws IOException {
        if (params.isReadonly()) throw new IOException("Readonly");
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

    public void writeChunk(ChunkOffset offset, Tag tag) throws IOException {
        if (params.isReadonly()) throw new IOException("Readonly");
        dirty = true;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TagOutputStream tagOut = new TagOutputStream(new DeflaterOutputStream(out));
        try {
            tagOut.writeTag(tag);
        } finally {
            tagOut.close();
        }
        byte[] data = out.toByteArray();
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
        //header.put(offset.getOffset()+1024, timeStamp);
        fd.seek(fileOffset);
        fd.writeInt(data.length);
        fd.write(2);
        fd.write(data);
        chunkStatus.put(offset, ChunkStatus.OK);
    }
}