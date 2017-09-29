package site.kason.netlib.ssl;

import site.kason.netlib.tcp.ReadTask;
import site.kason.netlib.tcp.Transfer;

/**
 *
 * @author Kason Yang
 */
public class SSLHandshakeReadTask implements ReadTask {

  private final SSLSession session;

  public SSLHandshakeReadTask(SSLSession session) {
    this.session = session;
  }

  @Override
  public boolean handleRead(Transfer transfer) throws Exception {
    if(session.isHandshaked()) return true;
    session.handshakeUnwrap(transfer);
    return session.isHandshaked();
  }

}
