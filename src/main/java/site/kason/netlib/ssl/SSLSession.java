package site.kason.netlib.ssl;

import lombok.SneakyThrows;
import site.kason.netlib.io.BufferUnderflowException;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.util.Logger;

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

  private final static Logger LOG = Logger.getLogger(SSLSession.class);

  private final Channel channel;

  private final SSLEngine sslEngine;

  private boolean handshaking;

  private boolean handshaked;

  private boolean finishHandshakePending = false;

  private IOBuffer handshakeWriteBuffer;

  private IOBuffer handshakeReadBuffer;

  public SSLSession(Channel channel, SSLEngine sslEngine) {
    this.channel = channel;
    this.sslEngine = sslEngine;
    javax.net.ssl.SSLSession sess = sslEngine.getSession();
    int maxPacketSize = sess.getPacketBufferSize();
    this.handshakeReadBuffer = IOBuffer.create(maxPacketSize);
    this.handshakeWriteBuffer = IOBuffer.create(maxPacketSize);

  }

  public Channel getChannel() {
    return channel;
  }

  public boolean isHandshaked() {
    return this.handshaked;
  }

  public void handleRead(IOBuffer in, IOBuffer out) throws SSLException, IOException {
    if (!isHandshaked()) {
      this.handshakeRead(in);
    }
    if (isHandshaked()) {
      this.decrypt(in, out);
    }
  }

  public void handleWrite(IOBuffer in, IOBuffer out) throws SSLException, IOException {
    if (this.isHandshaked()) {
      this.encrypt(in, out);
    } else {
      this.handshakeWrite(out);
    }
  }

  private void handshakeRead(IOBuffer in) throws IOException {
    //System.out.println("handling unwrap:" + channel);
    //this.readToBuffer(transfer);
    if (this.finishHandshakePending) {
      this.finishHandshake();
    } else {
      this.handshakeReadBuffer.clear();
      this.handshakeReadBuffer.push(in);
      this.prepareNextOperationOfHandshake(sslEngine.getHandshakeStatus());
      in.moveReadPosition(-handshakeReadBuffer.getReadableSize());
    }
  }

  private void handshakeWrite(IOBuffer out) throws SSLException, IOException {
    if (handshakeWriteBuffer.getReadableSize() > 0) {
      out.push(this.handshakeWriteBuffer);
      this.handshakeWriteBuffer.compact();
      return;
    }
    if (this.finishHandshakePending) {
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
      channel.requestRead();
      channel.requestWrite();
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
      LOG.debug("%s: handshake consumes %d bytes", channel, byteConsumed);
      handshakeReadBuffer.moveReadPosition(byteConsumed);
    }
    if (byteProduced > 0) {
      LOG.debug("%s: handshake produces %d bytes", channel, byteProduced);
      handshakeWriteBuffer.setWritePosition(handshakeWriteBuffer.getWritePosition() + byteProduced);
      channel.requestWrite();
    }
    SSLEngineResult.Status status = result.getStatus();
    if (status == SSLEngineResult.Status.OK) {
      this.prepareNextOperationOfHandshake(result.getHandshakeStatus());
    } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
      channel.requestWrite();
    } else if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
      channel.requestRead();
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
    LOG.debug("%s: handshake finished", channel);
    LOG.debug("%s: remaining read buffer %d", channel, handshakeReadBuffer.getReadableSize());
    this.handshaked = true;
    this.handshaking = false;
    channel.requestRead();
    channel.requestWrite();
    //System.out.println("handshake finished.");
  }

  public int getApplicationBufferSize() {
    return this.sslEngine.getSession().getApplicationBufferSize();
  }

  public int getPacketBufferSize() {
    return this.sslEngine.getSession().getPacketBufferSize();
  }

}
