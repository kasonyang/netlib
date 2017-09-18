package site.kason.netlib.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import site.kason.netlib.io.BufferUnderflowException;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.BufferTransfer;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.Transfer;

/**
 *
 * @author Kason Yang
 */
class SSLSession {

    private final Channel channel;

    private final BufferTransfer bufferTransfer;

    private final SSLEngine sslEngine;

    private boolean handshaking;

    private boolean handshaked;

    private final IOBuffer handshakeWriteBuffer;

    private final IOBuffer handshakeReadBuffer;
    
    private final ByteBuffer inNetBuffer;
    
    private final ByteBuffer inAppBuffer;
    
    private final ByteBuffer outNetBuffer;
    
    private final ByteBuffer outAppBuffer;

    public SSLSession(Channel channel, BufferTransfer bufferTransfer,SSLEngine sslEngine) {
        this.channel = channel;
        this.bufferTransfer = bufferTransfer;
        this.sslEngine = sslEngine;
        javax.net.ssl.SSLSession sess = sslEngine.getSession();
        int maxPacketSize = sess.getPacketBufferSize();
        this.handshakeReadBuffer = IOBuffer.create(maxPacketSize);
        this.handshakeWriteBuffer = IOBuffer.create(maxPacketSize);
        int maxAppSize = sess.getApplicationBufferSize();
        this.inNetBuffer = ByteBuffer.allocate(maxPacketSize);
        this.outNetBuffer = ByteBuffer.allocate(maxPacketSize);
        this.inAppBuffer = ByteBuffer.allocate(maxAppSize);
        this.outAppBuffer = ByteBuffer.allocate(maxAppSize);
    }

    public Channel getChannel() {
        return channel;
    }

    public BufferTransfer getBufferTransfer() {
        return bufferTransfer;
    }

    public boolean isHandshaked() {
        return this.handshaked;
    }

    public void handshakeUnwrap(Transfer transfer) throws IOException {
        handshakeReadBuffer.compact();
        transfer.read(handshakeReadBuffer);
        inNetBuffer.reset();
        int maxSize = Math.min(handshakeReadBuffer.getReadableSize(), inNetBuffer.limit());
        handshakeReadBuffer.peek(inNetBuffer.array(), 0, maxSize);
        inNetBuffer.position(0).limit(maxSize);
        SSLEngineResult result = sslEngine.unwrap(inNetBuffer, inAppBuffer);
        HandshakeStatus hs = result.getHandshakeStatus();
        int consumed = result.bytesConsumed();
        handshakeReadBuffer.skip(consumed);
        this.prepareNextOperationOfHandshake(transfer,hs);
    }

    public void handshakeWrap(Transfer transfer) throws SSLException, IOException {
        outNetBuffer.reset();
        SSLEngineResult result = sslEngine.wrap(ByteBuffer.allocate(0), outNetBuffer);
        HandshakeStatus hs = result.getHandshakeStatus();
        outNetBuffer.flip();
        handshakeWriteBuffer.push(outNetBuffer.array(), 0, outNetBuffer.limit());
        //TODO maybe not written
        transfer.write(handshakeWriteBuffer);
        this.prepareNextOperationOfHandshake(transfer,hs);
    }

    private void prepareNextOperationOfHandshake(Transfer transfer,HandshakeStatus hs) throws IOException {
        if (hs == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = sslEngine.getDelegatedTask()) != null) {
                runnable.run();
            }
            hs = sslEngine.getHandshakeStatus();
            prepareNextOperationOfHandshake(transfer,hs);
        } else if (hs == HandshakeStatus.NEED_WRAP) {
            if(handshakeWriteBuffer.getReadableSize()>0){
                this.handshakeWrap(transfer);
            }else{
                channel.prepareWrite();
            }
        } else if (hs == HandshakeStatus.NEED_UNWRAP) {
            if(handshakeReadBuffer.getReadableSize()>0){
                this.handshakeUnwrap(transfer);
            }else{
                channel.prepareRead();
            }
        } else if (hs == HandshakeStatus.FINISHED) {
            this.handshaked = true;
            this.handshaking = false;
            channel.prepareRead();
            channel.prepareWrite();
        }
    }

    public void encrypt(IOBuffer source, IOBuffer dest) throws SSLException,BufferUnderflowException {
        inNetBuffer.compact();
        inAppBuffer.compact();
        int maxSize = Math.min(inAppBuffer.remaining(), source.getReadableSize());
        if(maxSize<=0) return;
        int oldAppPosition = inAppBuffer.position();
        source.poll(inAppBuffer.array(), oldAppPosition, maxSize);
        inAppBuffer.position(0).limit(oldAppPosition + maxSize);
        sslEngine.wrap(inAppBuffer , inNetBuffer);
        inNetBuffer.flip();
        int writeSize = Math.min(dest.getWritableSize(),inNetBuffer.limit());
        dest.push(inNetBuffer.array() , 0 , writeSize);
        inNetBuffer.position(writeSize);
    }

    public void decrypt(IOBuffer source, IOBuffer dest) throws SSLException {
        outAppBuffer.compact();
        outNetBuffer.compact();
        int maxSize = Math.min(source.getReadableSize() , outAppBuffer.remaining());
        source.poll(outAppBuffer.array(),outAppBuffer.position(),maxSize);
        outAppBuffer.position(outAppBuffer.position()+maxSize);
        outAppBuffer.flip();
        sslEngine.unwrap(outAppBuffer, outNetBuffer);
        outNetBuffer.flip();
        int writeSize = Math.min(outNetBuffer.limit(),dest.getWritableSize());
        dest.push(outNetBuffer.array(), 0,writeSize);
    }

}
