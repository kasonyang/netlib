package site.kason.netlib.ssl;

import lombok.SneakyThrows;
import site.kason.netlib.io.BufferUnderflowException;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.pipeline.CodecInitProgress;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 * @author Kason Yang
 */
public class SSLSession {

  private final Channel channel;

  private final SSLEngine sslEngine;

  private boolean handshaking;

  private boolean handshaked;

  private boolean finishHandshakePending = false;

  private final IOBuffer handshakeWriteBuffer;

  private final IOBuffer handshakeReadBuffer;

  private final CodecInitProgress progress;

  public SSLSession(Channel channel, SSLEngine sslEngine, CodecInitProgress progress) {
    this.channel = channel;
    this.sslEngine = sslEngine;
    javax.net.ssl.SSLSession sess = sslEngine.getSession();
    int maxPacketSize = sess.getPacketBufferSize();
    this.handshakeReadBuffer = IOBuffer.create(maxPacketSize);
    this.handshakeWriteBuffer = IOBuffer.create(maxPacketSize);
    this.progress = progress;

  }

  public Channel getChannel() {
    return channel;
  }

  public boolean isHandshaked() {
    return this.handshaked;
  }

  public void handleRead(IOBuffer in, IOBuffer out) throws SSLException, IOException {
    if (!this.isHandshaked()) {
      throw new IllegalStateException();
    }
    this.decrypt(in, out);
  }

  public void handleWrite(IOBuffer in, IOBuffer out) throws SSLException, IOException {
    if (!this.isHandshaked()) {
      throw new IllegalStateException();
    }
    this.encrypt(in, out);
  }

  public void handshakeRead(IOBuffer in) {
    if (isHandshaked()) {
      return;
    }
    //System.out.println("handling unwrap:" + channel);
    //this.readToBuffer(transfer);
    channel.pauseRead();
    this.handshakeReadBuffer.compact();
    this.handshakeReadBuffer.push(in);
    if (this.finishHandshakePending) {
      this.finishHandshake();
    } else {
      this.prepareNextOperationOfHandshake(sslEngine.getHandshakeStatus());
    }
  }

  public void handshakeWrite(IOBuffer out) {
    if (isHandshaked()) {
      return;
    }
    channel.pauseWrite();
    //System.out.println("handling wrap:" + channel);
    this.handshakeWriteBuffer.compact();
    out.push(this.handshakeWriteBuffer);
    if (this.finishHandshakePending) {
      if (out.getReadableSize() > 0) {
        channel.continueWrite();
        return;
      }
      this.finishHandshake();
    } else {
      this.prepareNextOperationOfHandshake(sslEngine.getHandshakeStatus());
    }
  }

  @SneakyThrows
  private void prepareNextOperationOfHandshake(HandshakeStatus hs) {
    ByteBuffer readBuffer = ByteBuffer.wrap(handshakeReadBuffer.array(), handshakeReadBuffer.getReadPosition(), handshakeReadBuffer.getReadableSize());
    ByteBuffer writeBuffer = ByteBuffer.wrap(handshakeWriteBuffer.array(), handshakeWriteBuffer.getWritePosition(), handshakeWriteBuffer.getWritableSize());
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
      channel.continueRead();
      channel.continueWrite();
      //System.out.println("handling FINISHED");
    } else if (hs == HandshakeStatus.NOT_HANDSHAKING) {
      sslEngine.beginHandshake();
      this.prepareNextOperationOfHandshake(sslEngine.getHandshakeStatus());
    } else {
      throw new RuntimeException("unknown handshake status:" + hs);
    }
  }

  private void handleResult(SSLEngineResult result) {
    int byteConsumed = result.bytesConsumed();
    int byteProduced = result.bytesProduced();
    if (byteConsumed > 0) {
      handshakeReadBuffer.moveReadPosition(byteConsumed);
    }
    if (byteProduced > 0) {
      handshakeWriteBuffer.setWritePosition(handshakeWriteBuffer.getWritePosition() + byteProduced);
      channel.continueWrite();
    }
    SSLEngineResult.Status status = result.getStatus();
    if (status == SSLEngineResult.Status.OK) {
      this.prepareNextOperationOfHandshake(result.getHandshakeStatus());
    } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
      channel.continueWrite();
    } else if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
      channel.continueRead();
    } else {
      throw new RuntimeException("unexpected status:" + status);
    }
  }

  private void encrypt(IOBuffer source, IOBuffer dest) throws SSLException, BufferUnderflowException {
    ByteBuffer srcBf = ByteBuffer.wrap(source.array(), source.getReadPosition(), source.getReadableSize());
    ByteBuffer outBf = ByteBuffer.wrap(dest.array(), dest.getWritePosition(), dest.getWritableSize());
    SSLEngineResult res = sslEngine.wrap(srcBf, outBf);
    int byteConsumed = res.bytesConsumed();
    int byteProduced = res.bytesProduced();
    source.moveReadPosition(byteConsumed);
    dest.setWritePosition(dest.getWritePosition() + byteProduced);
  }

  private void decrypt(IOBuffer source, IOBuffer dest) throws SSLException {
    ByteBuffer srcBf = ByteBuffer.wrap(source.array(), source.getReadPosition(), source.getReadableSize());
    ByteBuffer outBf = ByteBuffer.wrap(dest.array(), dest.getWritePosition(), dest.getWritableSize());
    SSLEngineResult res = sslEngine.unwrap(srcBf, outBf);
    int byteConsumed = res.bytesConsumed();
    int byteProduced = res.bytesProduced();
    source.moveReadPosition(byteConsumed);
    dest.setWritePosition(dest.getWritePosition() + byteProduced);
  }

  private void finishHandshake() {
    this.handshaked = true;
    this.handshaking = false;
    channel.continueRead();
    channel.continueWrite();
    progress.done();
    //System.out.println("handshake finished.");
  }

  public int getApplicationBufferSize() {
    return this.sslEngine.getSession().getApplicationBufferSize();
  }

  public int getPacketBufferSize() {
    return this.sslEngine.getSession().getPacketBufferSize();
  }

}
