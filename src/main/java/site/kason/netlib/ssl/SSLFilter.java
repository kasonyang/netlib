package site.kason.netlib.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.ChannelFilter;
import site.kason.netlib.tcp.ReadTask;
import site.kason.netlib.tcp.WriteTask;
import site.kason.netlib.tcp.pipeline.Pipeline;

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
      return task;
    }

    @Override
    public ReadTask filterRead(ReadTask task) {
      return task;
    }

    @Override
    public void installed(Channel ch) {
      this.channel = ch;
      this.session = new SSLSession(ch, sslEngine);
      ch.setEncodePipeline(new Pipeline(new SSLEncodeProcessor(session)));
      ch.setDecodePipeline(new Pipeline(new SSLDecodeProcessor(session)));
        //ch.write(new SSLHandshakeWriteTask(session));
        //ch.read(new SSLHandshakeReadTask(session));
//        ch.write(new WriteTask() {
//            @Override
//            public boolean handleWrite(Transfer transfer) {
//                return true;
//            }
//        });
//        ch.read(new ReadTask() {
//            @Override
//            public boolean handleRead(Transfer transfer) {
//                return true;
//            }
//        });
    }

}
