package site.kason.netlib.ssl;

import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.ReadTask;
import site.kason.netlib.tcp.WriteTask;
import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.pipeline.CodecInitProgress;
import site.kason.netlib.tcp.pipeline.Processor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * @author Kason Yang
 */
public class SSLCodec implements Codec {

  private final SSLContext context;
  private final boolean isClientMode;
  private SSLEncodeProcessor encoder;
  private SSLDecodeProcessor decoder;

  public SSLCodec(SSLContext context, boolean isClientMode) {
    this.context = context;
    this.isClientMode = isClientMode;
  }

  @Override
  public void init(Channel channel, CodecInitProgress progress) {
    SSLEngine sslEngine = context.createSSLEngine();
    sslEngine.setUseClientMode(isClientMode);
    final SSLSession session = new SSLSession(channel, sslEngine, progress);
    channel.read(new ReadTask() {
      @Override
      public boolean handleRead(Channel channel, IOBuffer buffer) throws Exception {
        session.handshakeRead(buffer);
        return session.isHandshaked();
      }
    });
    channel.write(new WriteTask() {
      @Override
      public boolean handleWrite(Channel channel, IOBuffer buffer) throws Exception {
        session.handshakeWrite(buffer);
        return session.isHandshaked();
      }
    });
    encoder = new SSLEncodeProcessor(session);
    decoder = new SSLDecodeProcessor(session);
  }

  @Override
  public Processor getEncoder() {
    return encoder;
  }

  @Override
  public Processor getDecoder() {
    return decoder;
  }

}
