package site.kason.netlib.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.pipeline.Processor;

/**
 *
 * @author Kason Yang
 */
public class SSLCodec implements Codec {

  private final SSLEngine sslEngine;
  private SSLSession session;

  private Processor encoder, decoder;

  public SSLCodec(Channel ch, SSLContext context, boolean clientMode) {
    sslEngine = context.createSSLEngine();
    sslEngine.setUseClientMode(clientMode);
    session = new SSLSession(ch, sslEngine);
    this.encoder = new SSLEncodeProcessor(session);
    this.decoder = new SSLDecodeProcessor(session);
  }

  @Override
  public boolean hasEncoder() {
    return true;
  }

  @Override
  public Processor getEncoder() {
    return encoder;
  }

  @Override
  public boolean hasDecoder() {
    return true;
  }

  @Override
  public Processor getDecoder() {
    return decoder;
  }

}
