package site.kason.netlib.ssl;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.pipeline.Processor;

/**
 *
 * @author Kason Yang
 */
public class SSLDecodeProcessor implements Processor {
  
  private final SSLSession sslSession;

  public SSLDecodeProcessor(SSLSession sslSession) {
    this.sslSession = sslSession;
  }

  @Override
  public int getMinInBufferSize() {
    return sslSession.getPacketBufferSize();
  }

  @Override
  public int getMinOutBufferSize() {
    return sslSession.getApplicationBufferSize();
  }

  @Override
  public void process(IOBuffer in, IOBuffer out) {
    if(sslSession.isHandshaked()){
      try {
        sslSession.decrypt(in, out);
      } catch (SSLException ex) {
        //TODO handle ex
        throw new RuntimeException(ex);
      }
    }else{
      try {
        sslSession.handshakeUnwrap(in);
      } catch (IOException ex) {
        //TODO handle ex
        throw new RuntimeException(ex);
      }
    }
    
  }

}
