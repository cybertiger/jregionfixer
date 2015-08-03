/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.File;
import java.util.EnumSet;

/**
 *
 * @author antony
 */
public class DefaultErrorHandler implements ErrorHandler {
    File lastWorld = null;
    File lastRegion = null;

    public DefaultErrorHandler() {
    }

    private void showWorld(Chunk chunk) {
        File world = chunk.getWorld();
        if (!world.equals(lastWorld)) {
            System.out.println(world.getPath());
            lastWorld = world;
        }
    }

    private void showRegion(Chunk chunk) {
        File region = chunk.getFile();
        if (!region.equals(lastRegion)) {
            System.out.println("    " + chunk.getRelativePath());
        }
    }

    @Override
    public ErrorAction corruptRegionFile(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        showWorld(chunk);
        lastRegion = chunk.getFile();
        System.out.println("    " + chunk.getRelativePath() + " Corrupt: " + message + " " + def.getDone());
        return def;
    }

    private void showWorldRegion(Chunk chunk) {
        showWorld(chunk);
        showRegion(chunk);
    }


    private ErrorAction printError(Chunk chunk, String error, String message, ErrorAction def) {
        showWorldRegion(chunk);
        System.out.println("        " + chunk.getX() + ", " + chunk.getZ() + " (relative: " + chunk.getOffset().getX() + ", " + chunk.getOffset().getZ() + ") " + error  + ": " + message + " " + def.getDone());
        return def;
    }

    @Override
    public ErrorAction corruptChunk(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Corrupt", message, def);
    }

    @Override
    public ErrorAction invalidXPosition(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Invalid x position", message, def);
    }

    @Override
    public ErrorAction invalidZPosition(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Invalid z position", message, def);
    }

    @Override
    public ErrorAction invalidEntities(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Invalid entities", message, def);
    }

    @Override
    public ErrorAction tooManyEntities(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Too many entities", message, def);
    }

    @Override
    public ErrorAction invalidTileEntities(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Invalid tile entities", message, def);
    }

    @Override
    public ErrorAction tooManyTileEntities(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Too many tile entities", message, def);
    }

    @Override
    public ErrorAction invalidTileEntity(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Invalid tile entity", message, def);
    }

    @Override
    public ErrorAction misplacedTileEntity(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def) {
        return printError(chunk, "Misplaced tile entity", message, def);
    }

}