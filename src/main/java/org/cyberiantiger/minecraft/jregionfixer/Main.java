/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.cyberiantiger.minecraft.jregionfixer.ErrorAction.*;
import org.cyberiantiger.minecraft.nbt.CompoundTag;
import org.cyberiantiger.minecraft.nbt.ListTag;
import org.cyberiantiger.minecraft.nbt.Tag;
import org.cyberiantiger.minecraft.nbt.TagType;

/**
 *
 * @author antony
 */
public class Main {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static void usage() {
        System.err.println("Usage: java -jar jregionfixer.jar [-m] <worldsave> [-b <backup>] <worldsave> [-b <backup>]....");
        System.err.println("     -m                       - Fix errors."); 
        System.err.println("     -b <path>                - Backup path (per world)"); 
        System.err.println("     -e <count>               - Check entities do not exceed count per chunk.");
        System.err.println("     -t <count>               - Check tile entities do not exceed count per chunk.");
        System.err.println("     -T                       - Check tile entities are inside chunk bounds.");
        System.err.println("     -D                       - Remove hoppers.");
        System.err.println("     -H                       - Remove droppers.");
        System.err.println("     -v                       - Increase verbosity.");
    }

    private static File getFile(String arg) {
        File ret = new File(arg);
        try {
            return ret.getCanonicalFile();
        } catch (IOException e) {
            return ret.getAbsoluteFile();
        }
    }

    public static void main(String[] args) {
        Set<File> paths = new HashSet<File>();
        Map<File,File> backups = new HashMap<File,File>();
        File lastPath = null;
        boolean parseFlags = true;
        CheckParameters params = new CheckParameters();
        DefaultErrorHandler results = new DefaultErrorHandler();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (parseFlags && arg.length() > 0 && arg.charAt(0) == '-') {
                if ("--".equals(arg)) {
                    parseFlags = false;
                } else if ("-m".equals(arg)) {
                    params.setReadonly(false);
                } else if ("-b".equals(arg)) {
                    if (lastPath == null) {
                        System.err.println("-b flag without preceeding worldsave");
                        usage();
                        System.exit(1);
                    }
                    if (backups.containsKey(lastPath)) {
                        System.err.println("You can only use -b once per worldsave");
                        usage();
                        System.exit(1);
                    }
                    if (++i >= args.length) {
                        System.err.println("-b requires an argument");
                        usage();
                        System.exit(1);
                    }
                    File backupPath = getFile(args[i]);
                    if (!backupPath.isDirectory() || !backupPath.canRead()) {
                        System.err.println("Cannot read backup directory: " + backupPath);
                        usage();
                        System.exit(1);
                    }
                    backups.put(lastPath, getFile(args[i]));
                } else if ("-e".equals(arg)) {
                    if (++i == args.length) {
                        System.err.println("-e <count> missing required argument.");
                        usage();
                        System.exit(1);
                    }
                    int count;
                    try {
                        count = Integer.parseInt(args[i]);
                    } catch (NumberFormatException ex) {
                        System.err.println("Not a number: " + args[i]);
                        usage();
                        System.exit(1);
                        return;
                    }
                    params.setCheckEntityCount(true);
                    params.setMaxEntities(count);
                } else if ("-t".equals(arg)) {
                    if (++i == args.length) {
                        System.err.println("-t <count> missing required argument.");
                        usage();
                        System.exit(1);
                    }
                    int count;
                    try {
                        count = Integer.parseInt(args[i]);
                    } catch (NumberFormatException ex) {
                        System.err.println("Not a number: " + args[i]);
                        usage();
                        System.exit(1);
                        return;
                    }
                    params.setCheckTileEntityCount(true);
                    params.setMaxTileEntities(count);
                } else if ("-T".equals(arg)) {
                    params.setCheckTileEntityLocation(true);
                } else if ("-H".equals(arg)) {
                    params.setDeleteHoppers(true);
                } else if ("-D".equals(arg)) {
                    params.setDeleteDroppers(true);
                } else if ("-v".equals(arg)) {
                    params.incrementVerbosity();
                } else {
                    System.err.println("Unexpected flag: " + arg);
                    usage();
                    System.exit(1);
                }
                continue;
            }
            lastPath = getFile(arg);
            if (!lastPath.isDirectory() || !lastPath.canRead()) {
                System.err.println("Cannot read world directory: " + lastPath);
                usage();
                System.exit(1);
            }
            if (!paths.add(lastPath)) {
                System.err.println("Duplicated world directory: " + lastPath);
                usage();
                System.exit(1);
            }
        }

        if (paths.isEmpty()) {
            usage();
            System.exit(0);
        }

        params.setPaths(paths);
        params.setBackups(backups);

        for (File file : paths) {
            boolean foundRegionFiles = false;
            File region = new File(file, "region");
            if (region.isDirectory()) {
                if (params.isVerbose(2)) System.err.println("Checking region files in: " + region);
                checkRegionFiles(file, region, results, params);
                foundRegionFiles = true;
            }
            region = new File(file, "DIM1/region");
            if (region.isDirectory()) {
                if (params.isVerbose(2)) System.err.println("Checking region files in: " + region);
                checkRegionFiles(file, region, results, params);
                foundRegionFiles = true;
            }
            region = new File(file, "DIM-1/region");
            if (region.isDirectory()) {
                if (params.isVerbose(2)) System.err.println("Checking region files in: " + region);
                checkRegionFiles(file, region, results, params);
                foundRegionFiles = true;
            }
            if (!foundRegionFiles) {
                if (params.isVerbose(1)) System.err.println("Skipping " + file.getPath() + " - count not find any region directories");
            }
        }
    }

    private static void checkRegionFiles(File world, File region, DefaultErrorHandler results, CheckParameters params) {
        for (File f : region.listFiles()) {
            if (f.isFile() && RegionFile.REGION_FILE.matcher(f.getName()).matches()) {
                if (params.isVerbose(2)) System.err.println("Checking region file: " + f);
                checkRegionFile(world, f, results, params);
            }
        }
    }

    private static void checkRegionFile(File world, File region, DefaultErrorHandler results, CheckParameters params) {
        try {
            RegionFile regionFile = new RegionFile(region, params.isReadonly());
            try {
                try {
                    regionFile.loadHeaders();
                } catch (IOException ex) {
                    results.corruptRegionFile(new Chunk(world, region, null, regionFile.getX(), regionFile.getZ()), ex.getMessage(), EnumSet.of(NONE), NONE);
                }
                checkRegionFile(world, region, regionFile, results, params);
            } finally {
                regionFile.close();
            }
        } catch (IOException ex) {
            if (params.isVerbose(1)) ex.printStackTrace(System.err);
        }
    }
    
    private static void checkRegionFile(File world, File region, RegionFile regionFile, DefaultErrorHandler results, CheckParameters params) {
        for (int i = 0; i < 1024; i++) {
            ChunkOffset offset = new ChunkOffset(i);
            Chunk chunk = new Chunk(world, region, offset, regionFile.getX(), regionFile.getZ());
            switch (regionFile.getChunkStatus(offset)) {
                case NOT_PRESENT:
                    break;
                case INVALID:
                    results.corruptChunk(chunk, "Region file header had corrupt chunk offset", EnumSet.of(NONE), NONE);
                    break;
                case OK:
                    try {
                        checkChunk(chunk, regionFile.readChunk(offset), results, params);
                    } catch (IOException ex) {
                        results.corruptChunk(chunk, ex.getMessage(), EnumSet.of(NONE), NONE);
                    }
                    break;
            }
        }
    }

    private static void checkChunk(Chunk chunk, Tag data, DefaultErrorHandler results, CheckParameters params) {
        if (data.getType() != TagType.COMPOUND) {
            results.corruptChunk(chunk, "Chunk data not a compound", EnumSet.of(NONE), NONE);
            // Fatal, no point performing further checks.
            return;
        }
        CompoundTag dataC = (CompoundTag) data;
        if (!dataC.containsKey("Level", TagType.COMPOUND)) {
            results.corruptChunk(chunk, "Chunk data missing \"Level\" compound.", EnumSet.of(NONE), NONE);
            // Fatal, no point performing further checks.
            return;
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
            results.invalidZPosition(chunk, "zPos: " + level.getInt("zPos") + " does not match expected zPos: " + chunk.getZ(), EnumSet.of(NONE), NONE);
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
    }
}
