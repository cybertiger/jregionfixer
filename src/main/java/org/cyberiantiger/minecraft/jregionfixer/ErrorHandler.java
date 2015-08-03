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
public interface ErrorHandler {

    ErrorAction corruptRegionFile(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction corruptChunk(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction invalidXPosition(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction invalidZPosition(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction invalidEntities(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction tooManyEntities(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction invalidTileEntities(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction tooManyTileEntities(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction invalidTileEntity(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);
    ErrorAction misplacedTileEntity(Chunk chunk, String message, EnumSet<ErrorAction> actions, ErrorAction def);

}
