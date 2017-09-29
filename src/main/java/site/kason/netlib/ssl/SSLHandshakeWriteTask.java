package site.kason.netlib.ssl;

import site.kason.netlib.tcp.Transfer;
import site.kason.netlib.tcp.WriteTask;

/**
 *
 * @author Kason Yang
 */
public class SSLHandshakeWriteTask implements WriteTask {

  private SSLSession session;

  public SSLHandshakeWriteTask(SSLSession session) {
    this.session = session;
  }

  @Override
  public boolean handleWrite(Transfer transfer) throws Exception {
    if(session.isHandshaked()) return true;
    session.handshakeWrap(transfer);
    return session.isHandshaked();
  }

}
