package site.kason.netlib.ssl;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import site.kason.netlib.io.BufferUnderflowException;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.pipeline.Processor;

/**
 *
 * @author Kason Yang
 */
public class SSLEncodeProcessor implements Processor {

  private final SSLSession sslSession;

  public SSLEncodeProcessor(SSLSession sslSession) {
    this.sslSession = sslSession;
  }

  @Override
  public int getMinInBufferSize() {
    return sslSession.getApplicationBufferSize();
  }

  @Override
  public int getMinOutBufferSize() {
    return sslSession.getPacketBufferSize();
  }

  @Override
  public void process(IOBuffer in, IOBuffer out) {
    try {
      sslSession.handleWrite(in, out);
    } catch (IOException ex) {
      throw new SSLEncodeException(ex);
    }
  }

}
