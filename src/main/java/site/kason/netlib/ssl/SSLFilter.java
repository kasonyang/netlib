package site.kason.netlib.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import site.kason.netlib.tcp.BufferTransfer;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.ChannelFilter;
import site.kason.netlib.tcp.ReadTask;
import site.kason.netlib.tcp.Transfer;
import site.kason.netlib.tcp.WriteTask;

/**
 *
 * @author Kason Yang
 */
public class SSLFilter implements ChannelFilter {

    private SSLSession session;

    private final SSLContext context;

    private final boolean clientMode;

    private Channel channel;
    private final SSLEngine sslEngine;

    public SSLFilter(SSLContext context, boolean clientMode) {
        this.context = context;
        this.clientMode = clientMode;
        sslEngine = context.createSSLEngine();
        sslEngine.setUseClientMode(clientMode);

    }

    @Override
    public WriteTask filterWrite(WriteTask task) {
        return new SSLWriteTask(session, task);
    }

    @Override
    public ReadTask filterRead(ReadTask task) {
        return new SSLReadTask(session, task);
    }

    @Override
    public void install(Channel ch) {
        this.channel = ch;
        this.session = new SSLSession(ch, new BufferTransfer(), sslEngine);
        ch.write(new WriteTask() {
            @Override
            public boolean handleWrite(Transfer transfer) {
                return true;
            }
        });
        ch.read(new ReadTask() {
            @Override
            public boolean handleRead(Transfer transfer) {
                return true;
            }
        });
    }

}
