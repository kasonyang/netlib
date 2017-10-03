package test.site.kason.netlib;

import java.io.IOException;
import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.tasks.ByteWriteTask;

public class ChannelHostTest {
  
  public static class StopException extends RuntimeException{
    
  }

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
    Channel client = atcp.createChannel();
    final ExceptionHandler exh = new ExceptionHandler() {
      @Override
      public void handleException(Channel ch, Exception ex) {
        if(ex instanceof StopException){
          try {
            ch.close();
          } catch (IOException ex1) {
            throw new RuntimeException(ex1);
          }
        }else{
          throw new RuntimeException(ex);
        }
      }
    };
    client.setExceptionHandler(exh);
    client.setConnectionHandler(new ConnectionHandler() {
      @Override
      public void channelConnected(final Channel ch) {
        log("client:connected");
        ch.write(new ByteWriteTask(data, 0, data.length));
        ch.read(new ReadTask() {
          private int counter = 0;
          @Override
          public boolean handleRead(Channel channel,IOBuffer b) {
            if(counter<3){//test prepareRead even if no new data arrived
              counter++;
              channel.prepareRead();
              return false;
            }
            int rlen = b.getReadableSize();
            if (rlen > 0) {
              log("client:read " + rlen + " bytes");
            }
            byte[] receivedData = new byte[b.getReadableSize()];
            b.poll(receivedData);
            assertArrayEquals(data, receivedData);
            throw new StopException();
            //atcp.stopListen();
            //return true;
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
        atcp.stopListen();
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
          public boolean handleRead(Channel ch,IOBuffer readBuffer) {
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
              ch.write(new ByteWriteTask(receivedData, 0, receivedData.length));
              //atcp.stopListen();
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
