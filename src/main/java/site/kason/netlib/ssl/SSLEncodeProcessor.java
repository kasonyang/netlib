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
    if(sslSession.isHandshaked()){
      try {
        sslSession.encrypt(in, out);
      } catch (SSLException ex) {
        throw new RuntimeException(ex);
      } catch (BufferUnderflowException ex) {
        throw new RuntimeException(ex);
      }
    }else{
      try {
        sslSession.handshakeWrap(out);
      } catch (IOException ex) {
        //TODO handle ex
        throw new RuntimeException(ex);
      }
    }
  }
  

}
