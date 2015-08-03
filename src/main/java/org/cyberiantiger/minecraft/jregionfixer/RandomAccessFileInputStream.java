/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.minecraft.jregionfixer;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 *
 * @author antony
 */
public class RandomAccessFileInputStream extends InputStream {
    private final int maxLength;
    private final RandomAccessFile file;
    private int offset = 0;

    public RandomAccessFileInputStream(RandomAccessFile file, int maxLength) throws IOException {
        this.file = file;
        this.maxLength = maxLength;
        if (file.getFilePointer() + maxLength > file.length()) {
            throw new IOException("Chunk data exceeds end of file maxLength: " + maxLength + " offset: " + file.getFilePointer() + " file length: " + file.length());
        }
    }

    @Override
    public int read() throws IOException {
        if (offset == maxLength) {
            return -1;
        }
        offset++;
        return file.readByte() & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (offset == maxLength) {
            return -1;
        }
        int maxRead = Math.min(maxLength - offset, len);
        int read = file.read(b, off, maxRead);
        offset += read;
        return read;
    }

    @Override
    public int available() throws IOException {
        return maxLength - offset;
    }

    @Override
    public long skip(long n) throws IOException {
        n = Math.min(n, maxLength - offset);
        offset += n;
        file.seek(file.getFilePointer() + n);
        return n;
    }
    
}
