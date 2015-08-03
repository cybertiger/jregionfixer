/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        if (params.isReadonly()) {
            return;
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
            RegionFile regionFile = new RegionFile(world, region, results, params);
            try {
                regionFile.performChecks();
            } finally {
                regionFile.close();
            }
        } catch (IOException ex) {
            if (params.isVerbose(1)) ex.printStackTrace(System.err);
        }
    }
}
