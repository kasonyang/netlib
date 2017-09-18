package site.kason.netlib.io;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Kason Yang
 */
public class IOBuffer {

    private final byte[] byteBuffer;

    private int limit;

    private int readOffset = 0;

    private int writeOffset = 0;

    private final List<IOBufferListener> listeners = new LinkedList();

    public static IOBuffer create(int capacity) {
        byte[] bs = new byte[capacity];
        return new IOBuffer(bs, 0, 0, bs.length);
    }

    protected void push0(byte[] data, int offset, int length) {
        int writableSize = this.getWritableSize();
        if (writableSize < length) {
            throw new BufferOverflowException(length, writableSize);
        }
        System.arraycopy(data, offset, byteBuffer, writeOffset, length);
        writeOffset += length;
        for (IOBufferListener lst : listeners) {
            lst.pushed(this);
        }
    }

    public void push(byte[] data, int offset, int length) {
        push0(data, offset, length);
    }

    public void push(byte[] data) {
        push0(data, 0, data.length);
    }

    public void peek(byte[] dest, int offset, int length) {
        int usedSize = this.getReadableSize();
        if (usedSize < length) {
            throw new BufferUnderflowException(length, usedSize);
        }
        System.arraycopy(byteBuffer, readOffset, dest, offset, length);
    }

    public void skip(int length) {
        int readableSize = this.getReadableSize();
        if(length>readableSize){
            throw new BufferOverflowException(length, readableSize);
        }
        this.readOffset += length;
        for (IOBufferListener lst : listeners) {
            lst.polled(this);
        }
    }

    protected void poll0(byte[] dest, int offset, int length) {
        this.peek(dest, offset, length);
        this.skip(length);
    }

    public void poll(byte[] dest, int offset, int length) {
        poll0(dest, offset, length);
    }

    public void poll(byte[] dest) {
        poll0(dest, 0, dest.length);
    }

    public void addListener(IOBufferListener listener) {
        this.listeners.add(listener);
    }

    protected IOBuffer(byte[] array, int readOffset, int writeOffset, int limit) {
        this.byteBuffer = array;
        this.readOffset = readOffset;
        this.writeOffset = writeOffset;
        this.limit = limit;
    }

    public int getWritableSize() {
        return limit - writeOffset;
    }

    public int getReadableSize() {
        return writeOffset - readOffset;
    }

    public void compact() {
        if (readOffset > 0) {
            int dataSize = this.getReadableSize();
            System.arraycopy(byteBuffer, readOffset, byteBuffer, 0, dataSize);
            readOffset = 0;
            writeOffset = dataSize;
        }
    }
    
    public int limit(int newLimit){
        if(newLimit>byteBuffer.length){
            throw new IllegalArgumentException("new limit is out of capacity");
        }
        int oldLimit = this.limit;
        this.limit = newLimit;
        return oldLimit;
    }
    
    public byte[] array(){
        return this.byteBuffer;
    }
    
    public int getReadPosition(){
        return this.readOffset;
    }
    
    public void setReadPosition(int newPosition){
        if(newPosition<0){
            throw new IllegalArgumentException("positive int required.");
        }
        if(newPosition>this.limit){
            throw new IllegalArgumentException("position is out of limit");
        }
        this.readOffset = newPosition;
    }
    
    public int getWritePosition(){
        return this.writeOffset;
    }
    
    public void setWritePosition(int newPosition){
        if(newPosition<0){
            throw new IllegalArgumentException("positive int required.");
        }
        if(newPosition>this.limit){
            throw new IllegalArgumentException("position is out of limit");
        }
        this.writeOffset = newPosition;
    }

}
