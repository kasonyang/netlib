package site.kason.netlib.ssl;

import javax.net.ssl.SSLException;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.Transfer;
import site.kason.netlib.tcp.WriteTask;

/**
 *
 * @author Kason Yang
 */
class SSLWriteTask implements WriteTask {

    private int p = 0;

    private boolean originalFinished = false;

    private WriteTask originalTask;

    private SSLSession session;

    private IOBuffer eBuffer = IOBuffer.create(40960);

    public SSLWriteTask(SSLSession session, WriteTask originalTask) {
        this.originalTask = originalTask;
        this.session = session;
    }

    @Override
    public boolean handleWrite(Transfer transfer) throws Exception {
        if(!session.isHandshaked()){
            session.handshakeWrap(transfer);
            return false;
        }
        if (p <= 0) {
            if (!originalFinished) {
                originalFinished = originalTask.handleWrite(session.getBufferTransfer());
                p++;
                session.getChannel().prepareWrite();
            }
            return false;
        } else {
            p--;
            IOBuffer btWriteBuffer = session.getBufferTransfer().getWriteBuffer();
            if (eBuffer.getReadableSize()> 0) {                
                transfer.write(eBuffer);
                p++;
                session.getChannel().prepareWrite();
                return false;
            } else if (btWriteBuffer.getReadableSize()> 0) {
                encrypt(btWriteBuffer, eBuffer);
                p++;
                session.getChannel().prepareWrite();
                return false;
            } else {
                return originalFinished;
            }
        }

    }

    private void encrypt(IOBuffer source, IOBuffer dest) {
        try {
            session.encrypt(source, dest);
        } catch (SSLException ex) {
            throw new RuntimeException(ex);
        }
    }

}
