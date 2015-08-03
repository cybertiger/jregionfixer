/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

/**
 *
 * @author antony
 */
public enum ErrorAction {
    NONE(""), RESTORE("[Restored]"), DELETE("[Deleted]"), FIX("[Fixed]");

    private String done;

    private ErrorAction(String done) {
        this.done = done;
    }

    public String getDone() {
        return done;
    }
}