package test.site.kason.netlib;

import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;

import org.junit.Test;
import site.kason.netlib.codec.DeflateCodec;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.ssl.SSLCodec;
import site.kason.netlib.ssl.SSLContextUtil;
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
    //SSLChannel svr = SSLChannelFactory.createFromKeyStore(true,keyStore,trustStore,pwd);
    doTest(9001,new CodecFactory() {
      @Override
      public List<Codec> createCodecs(Channel ch) {
        return Arrays.asList(createSSLCodec(ch, false));
      }
    },new CodecFactory() {
      @Override
      public List<Codec> createCodecs(Channel ch) {
        return Arrays.asList(createSSLCodec(ch, true));
      }
    });
  }

  @Test
  public void test() throws Exception {
    doTest(9002, null,null);
  }
  
  @Test
  public void testDeflate() throws Exception{
    CodecFactory cf = new CodecFactory() {
      @Override
      public List<Codec> createCodecs(Channel ch) {
        return Arrays.asList((Codec)new DeflateCodec());
      }
    };
    doTest(9003,cf,cf);
  }
  
  @Test
  public void testDeflateAndSSL() throws Exception{
    doTest(9004,new CodecFactory() {
      @Override
      public List<Codec> createCodecs(Channel ch) {
        return Arrays.asList(createSSLCodec(ch, false),new DeflateCodec());
      }
    },new CodecFactory() {
      @Override
      public List<Codec> createCodecs(Channel ch) {
        return Arrays.asList(createSSLCodec(ch, true),new DeflateCodec());
      }
    });
  }

  public void doTest(int port, final CodecFactory serverCodecFactory,final CodecFactory clientCodecFactory) throws Exception {
    final byte[] data = new byte[]{3, 4, 5, 6, 7, 8, 9, 3, 7, 9, 3};
    final ChannelHost atcp = ChannelHost.create();
    SocketAddress addr = new InetSocketAddress(port);
    Channel client = atcp.createChannel();
    final ExceptionHandler exh = new ExceptionHandler() {
      @Override
      public void handleException(Channel ch, Exception ex) {
        if(ex instanceof StopException){
          ch.close();
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
        if(serverCodecFactory!=null){
          for(Codec c:serverCodecFactory.createCodecs(ch)){
            ch.addCodec(c);
          }
        }
        log("server accepted:" + ch.toString());
        //final IOBuffer readBuffer = IOBuffer.createFromKeyStore(data.length);
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
    if(clientCodecFactory!=null){
      for(Codec c:clientCodecFactory.createCodecs(client)){
        client.addCodec(c);
      }
    }
    atcp.listen();
    //server.close();
    client.close();
  }
  
  private Codec createSSLCodec(Channel ch,boolean clientMode){
    File keyStoreFile = new File("sslclientkeys");
    //String trustStore = "sslclientkeys";
    String pwd = "net-lib";
    try{
    SSLContext context = SSLContextUtil.createFromKeyStore(keyStoreFile, pwd);
    return new SSLCodec(ch,context, clientMode);
    }catch(Exception ex){
      throw new RuntimeException(ex);
    }
  }

  private void log(String msg) {
    System.out.println(msg);
  }

}
