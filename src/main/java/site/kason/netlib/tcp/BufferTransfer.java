package site.kason.netlib.tcp;

import java.io.IOException;
import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public class BufferTransfer implements Transfer {

    private final IOBuffer writeBuffer;

    private final IOBuffer readBuffer;

    public BufferTransfer() {
      this(4096,4096);
    }

    public BufferTransfer(int readBufferSize,int writeBufferSize) {
      writeBuffer = IOBuffer.create(writeBufferSize);
      readBuffer = IOBuffer.create(readBufferSize);
    }
    
    

    @Override
    public int write(IOBuffer buffer) throws IOException {
        writeBuffer.compact();
        int maxSize = Math.min(buffer.getReadableSize(), writeBuffer.getWritableSize());
        if (maxSize > 0) {
            System.arraycopy(buffer.array(), buffer.getReadPosition(), writeBuffer.array(), writeBuffer.getWritePosition(), maxSize);
            buffer.skip(maxSize);
            writeBuffer.setWritePosition(writeBuffer.getWritePosition() + maxSize);
        }
        return maxSize;
    }

    @Override
    public int read(IOBuffer buffer) throws IOException {
        int maxSize = Math.min(readBuffer.getReadableSize(), buffer.getWritableSize());
        if (maxSize > 0) {
            System.arraycopy(readBuffer.array(), readBuffer.getReadPosition(), buffer.array(), buffer.getWritePosition(), maxSize);
            readBuffer.skip(maxSize);
            buffer.setWritePosition(buffer.getWritePosition() + maxSize);
        }
        return maxSize;
    }

    public IOBuffer getWriteBuffer() {
        return writeBuffer;
    }

    public IOBuffer getReadBuffer() {
        return readBuffer;
    }

}
