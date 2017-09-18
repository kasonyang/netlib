package site.kason.netlib.ssl;

import java.io.IOException;
import javax.net.ssl.SSLException;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.BufferTransfer;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.ReadTask;
import site.kason.netlib.tcp.Transfer;

/**
 *
 * @author Kason Yang
 */
class SSLReadTask implements ReadTask {

    private int p = 0;

    private IOBuffer rawBuffer = IOBuffer.create(40960);

    private ReadTask originalTask;

    private SSLSession session;

    public SSLReadTask(SSLSession session, ReadTask originalTask) {
        this.originalTask = originalTask;
        this.session = session;
    }

    @Override
    public boolean handleRead(Transfer transfer) throws Exception {
        if(!session.isHandshaked()){
            session.handshakeUnwrap(transfer);
            return false;
        }
        BufferTransfer bufferTransfer = session.getBufferTransfer();
        Channel originalChannel = session.getChannel();
        IOBuffer btrBuffer = bufferTransfer.getReadBuffer();
        if (p <= 0) {
            if (btrBuffer.getReadableSize()> 0) {
                return originalTask.handleRead(bufferTransfer);
            } else {
                p++;
                originalChannel.prepareRead();
                return false;
            }
        } else {
            p--;
            rawBuffer.compact();
            transfer.read(rawBuffer);
            decrypt(rawBuffer, btrBuffer);
            if (btrBuffer.getReadableSize()<= 0) {
                p++;
                originalChannel.prepareRead();
            } else {
                originalChannel.prepareRead();
            }
            return false;
        }
    }

    private void decrypt(IOBuffer source, IOBuffer dest) {
        try {
            session.decrypt(source, dest);
        } catch (SSLException ex) {
            throw new RuntimeException(ex);
        }
    }

}
