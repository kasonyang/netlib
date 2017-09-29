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
    
    private boolean finishHandshakePending = false;

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
      //System.out.println("handling unwrap:" + channel);
      this.readToBuffer(transfer);
      if(this.finishHandshakePending){
        this.finishHandshake();
      }else{
        this.prepareNextOperationOfHandshake(sslEngine.getHandshakeStatus());
      }
    }

    //TODO rename to handleWrite
    public void handshakeWrap(Transfer transfer) throws SSLException, IOException {
      //System.out.println("handling wrap:" + channel);
      this.writeToTransfer(transfer);
      if(this.finishHandshakePending){
        this.finishHandshake();
      }else{
        this.prepareNextOperationOfHandshake(sslEngine.getHandshakeStatus());
      }
    }

    private void prepareNextOperationOfHandshake(HandshakeStatus hs) throws IOException {
        ByteBuffer readBuffer = ByteBuffer.wrap(handshakeReadBuffer.array(),handshakeReadBuffer.getReadPosition(),handshakeReadBuffer.getReadableSize());
        ByteBuffer writeBuffer = ByteBuffer.wrap(handshakeWriteBuffer.array(),handshakeWriteBuffer.getWritePosition(),handshakeWriteBuffer.getWritableSize());
        if (hs == HandshakeStatus.NEED_TASK) {
          Runnable runnable;
          while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
          }
          hs = sslEngine.getHandshakeStatus();
          prepareNextOperationOfHandshake(hs);
        } else if (hs == HandshakeStatus.NEED_WRAP) {
          SSLEngineResult wrapResult = sslEngine.wrap(readBuffer, writeBuffer);
          this.handleResult(wrapResult);
        } else if (hs == HandshakeStatus.NEED_UNWRAP) {
          SSLEngineResult unwrapResult = sslEngine.unwrap(readBuffer, writeBuffer);
          this.handleResult(unwrapResult);
        } else if (hs == HandshakeStatus.FINISHED) {
          this.finishHandshakePending = true;
          channel.prepareRead();
          channel.prepareWrite();
          //System.out.println("handling FINISHED");
        } else if (hs == HandshakeStatus.NOT_HANDSHAKING){
          sslEngine.beginHandshake();
          this.prepareNextOperationOfHandshake(sslEngine.getHandshakeStatus());
        }else{
          throw new RuntimeException("unknown handshake status:" + hs);
        }
    }
    
    private void handleResult(SSLEngineResult result) throws IOException{
      int byteConsumed = result.bytesConsumed();
      int byteProduced = result.bytesProduced();
      if(byteConsumed>0){
        handshakeReadBuffer.skip(byteConsumed);
      }
      if(byteProduced>0){
        handshakeWriteBuffer.setWritePosition(handshakeWriteBuffer.getWritePosition()+byteProduced);
        channel.prepareWrite();
      }
      SSLEngineResult.Status status = result.getStatus();
      if(status==SSLEngineResult.Status.OK){
        this.prepareNextOperationOfHandshake(result.getHandshakeStatus());
      }else if(status == SSLEngineResult.Status.BUFFER_OVERFLOW){
        channel.prepareWrite();
      }else if(status == SSLEngineResult.Status.BUFFER_UNDERFLOW){
        channel.prepareRead();
      }else if(status == SSLEngineResult.Status.CLOSED){
        //TODO handle closed
        throw new RuntimeException("channel closed.");
      }else{
        throw new RuntimeException("unexpected status:"+status);
      }
    }
    
    private void readToBuffer(Transfer transfer) throws IOException{
      handshakeReadBuffer.compact();
      while(handshakeReadBuffer.getWritableSize()>0 && transfer.read(handshakeReadBuffer)>0){
        //System.out.println("read");
      }
    }
    
    private void writeToTransfer(Transfer transfer) throws IOException{
      while(handshakeWriteBuffer.getReadableSize()>0 && transfer.write(handshakeWriteBuffer)>0){
        //System.out.println("written");
      }
      handshakeWriteBuffer.compact();
    }

    public void encrypt(IOBuffer source, IOBuffer dest) throws SSLException,BufferUnderflowException {
      ByteBuffer srcBf = ByteBuffer.wrap(source.array(),source.getReadPosition(),source.getReadableSize());
      ByteBuffer outBf = ByteBuffer.wrap(dest.array(),dest.getWritePosition(),dest.getWritableSize());
      SSLEngineResult res = sslEngine.wrap(srcBf, outBf);
      int byteConsumed = res.bytesConsumed();
      int byteProduced = res.bytesProduced();
      source.skip(byteConsumed);
      dest.setWritePosition(dest.getWritePosition()+byteProduced);
    }

    public void decrypt(IOBuffer source, IOBuffer dest) throws SSLException {
      ByteBuffer srcBf = ByteBuffer.wrap(source.array(),source.getReadPosition(),source.getReadableSize());
      ByteBuffer outBf = ByteBuffer.wrap(dest.array(),dest.getWritePosition(),dest.getWritableSize());
      SSLEngineResult res = sslEngine.unwrap(srcBf, outBf);
      int byteConsumed = res.bytesConsumed();
      int byteProduced = res.bytesProduced();
      source.skip(byteConsumed);
      dest.setWritePosition(dest.getWritePosition()+byteProduced);
    }
    
    private void finishHandshake(){
      this.handshaked = true;
      this.handshaking = false;    
      channel.prepareRead();
      channel.prepareWrite();
      channel.prepareRead();
      channel.prepareWrite();
      channel.prepareRead();
      //System.out.println("handshake finished.");
    }

}
