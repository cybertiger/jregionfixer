/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author antony
 */
public class CheckParameters {

    private Set<File> paths;
    private Map<File, File> backups;
    private boolean readonly = true;
    private boolean checkEntityCount = false;
    private int maxEntities = 300;
    private boolean checkTileEntityCount = false;
    private boolean checkTileEntityLocation = false;
    private int maxTileEntities = 300;
    private int verbosity = 0;
    private boolean interactive = false;
    private boolean deleteHoppers = false;
    private boolean deleteDroppers = false;

    public Set<File> getPaths() {
        return paths;
    }

    public void setPaths(Set<File> paths) {
        this.paths = paths;
    }

    public Map<File, File> getBackups() {
        return backups;
    }

    public void setBackups(Map<File, File> backups) {
        this.backups = backups;
    }

    public boolean isReadonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public boolean isCheckEntityCount() {
        return checkEntityCount;
    }

    public void setCheckEntityCount(boolean checkEntityCount) {
        this.checkEntityCount = checkEntityCount;
    }

    public int getMaxEntities() {
        return maxEntities;
    }

    public void setMaxEntities(int maxEntities) {
        this.maxEntities = maxEntities;
    }

    public boolean isCheckTileEntityCount() {
        return checkTileEntityCount;
    }

    public void setCheckTileEntityCount(boolean checkTileEntityCount) {
        this.checkTileEntityCount = checkTileEntityCount;
    }

    public boolean isCheckTileEntityLocation() {
        return checkTileEntityLocation;
    }

    public void setCheckTileEntityLocation(boolean checkTileEntityLocation) {
        this.checkTileEntityLocation = checkTileEntityLocation;
    }

    public int getMaxTileEntities() {
        return maxTileEntities;
    }

    public void setMaxTileEntities(int maxTileEntities) {
        this.maxTileEntities = maxTileEntities;
    }

    public int getVerbosity() {
        return verbosity;
    }

    public void setVerbosity(int verbosity) {
        this.verbosity = verbosity;
    }

    public void incrementVerbosity() {
        this.verbosity++;
    }

    public boolean isVerbose(int i) {
        return this.verbosity >= i;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public boolean isDeleteHoppers() {
        return deleteHoppers;
    }

    public void setDeleteHoppers(boolean deleteHoppers) {
        this.deleteHoppers = deleteHoppers;
    }

    public boolean isDeleteDroppers() {
        return deleteDroppers;
    }

    public void setDeleteDroppers(boolean deleteDroppers) {
        this.deleteDroppers = deleteDroppers;
    }
}
