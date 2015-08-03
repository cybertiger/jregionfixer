/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.IOException;

/**
 *
 * @author antony
 */
public class CorruptChunkException extends IOException {
    
    public CorruptChunkException(String message) {
        super(message);
    }
}
