package test.site.kason.netlib;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.net.ssl.SSLContext;

import org.junit.Test;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.ssl.SSLCodec;
import site.kason.netlib.ssl.SSLContextFactory;
import site.kason.netlib.tcp.AcceptHandler;
import site.kason.netlib.tcp.ChannelHost;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.ConnectionHandler;
import site.kason.netlib.tcp.ServerChannel;
import site.kason.netlib.tcp.ReadTask;
import site.kason.netlib.tcp.ExceptionHandler;
import site.kason.netlib.tcp.WriteTask;
import site.kason.netlib.tcp.pipeline.Codec;

public class ChannelHostTest {

  @Test
  public void testSSL() throws Exception {
    //SSLChannel svr = SSLChannelFactory.create(true,keyStore,trustStore,pwd);
    doTest(9001, true);
  }

  @Test
  public void test() throws Exception {
    doTest(9002, false);
  }

  public void doTest(int port,final boolean useSSL) throws Exception {
    final byte[] data = new byte[]{3, 4, 5, 6, 7, 8, 9, 3, 7, 9, 3};
    final ChannelHost atcp = ChannelHost.create();
    SocketAddress addr = new InetSocketAddress(port);
    final Channel client = atcp.createChannel();
    final IOBuffer writeBuffer = IOBuffer.create(data.length);
    writeBuffer.push(data);
    final ExceptionHandler exh = new ExceptionHandler() {
      @Override
      public void handleException(Channel ch, Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    client.setExceptionHandler(exh);
    client.setConnectionHandler(new ConnectionHandler() {
      @Override
      public void channelConnected(Channel ch) {
        log("client:connected");
        client.write(new WriteTask() {
          @Override
          public boolean handleWrite(IOBuffer buffer) {
            log("client:handling write");
            buffer.push(writeBuffer);
            int remaining = writeBuffer.getReadableSize();
            if (remaining > 0) {
              client.prepareWrite();
            }
            return remaining <= 0;
          }

        });
        client.read(new ReadTask() {
          @Override
          public boolean handleRead(IOBuffer b) {
            int rlen = b.getReadableSize();
            if (rlen > 0) {
              log("client:read " + rlen + " bytes");
            }
            //atcp.stopListen();
            return true;
          }

        });
      }

      @Override
      public void channelConnectFailed(Channel ch, Exception ex) {
        fail("client:connection failed");
      }

      @Override
      public void channelClosed(Channel ch) {
        log("channel closed:" + ch);
      }

    });
    ServerChannel server = atcp.createServerChannel(addr, new AcceptHandler() {
      @Override
      public void accepted(Channel ch) {
        ch.setExceptionHandler(exh);
        if (useSSL) {
          ch.addCodec(createSSLCodec(ch, false));
        }
        log("server accepted:" + ch.toString());
        //final IOBuffer readBuffer = IOBuffer.create(data.length);
        ch.read(new ReadTask() {
          @Override
          public boolean handleRead(IOBuffer readBuffer) {
            log("server read");
            int readSize = readBuffer.getReadableSize();
            log("server:read " + readSize + " bytes");
            if (readSize == -1) {
              fail("connection closed.");
              return true;
            }
            if (readBuffer.getReadableSize() >= data.length) {
              byte[] receivedData = new byte[data.length];
              readBuffer.poll(receivedData);
              assertArrayEquals(data, receivedData);
              atcp.stopListen();
              return true;
            } else {
              return false;
            }
          }

        });
      }

    });
    client.connect(addr);
    if (useSSL) {
      client.addCodec(createSSLCodec(client, true));
    }
    atcp.listen();
    //server.close();
    client.close();
  }
  
  private Codec createSSLCodec(Channel ch,boolean clientMode){
    String keyStore = "sslclientkeys";
    //String trustStore = "sslclientkeys";
    String pwd = "net-lib";
    try{
    SSLContext context = SSLContextFactory.create(keyStore, pwd);
    return new SSLCodec(ch,context, clientMode);
    }catch(Exception ex){
      throw new RuntimeException(ex);
    }
  }

  private void log(String msg) {
    System.out.println(msg);
  }

}
