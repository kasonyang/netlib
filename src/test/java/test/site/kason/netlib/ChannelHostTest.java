package test.site.kason.netlib;

import org.junit.Assert;
import org.junit.Test;
import site.kason.netlib.codec.DeflateCodec;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.ssl.SSLCodec;
import site.kason.netlib.ssl.SSLContextUtil;
import site.kason.netlib.tcp.*;
import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.tasks.ByteWriteTask;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public class ChannelHostTest {
  
  public static class StopException extends RuntimeException{
    
  }

  @Test
  public void testSSL() throws Exception {
    //SSLChannel svr = SSLChannelFactory.createFromKeyStore(true,keyStore,trustStore,pwd);
    doTest(
        9101,
        ch -> Collections.singletonList(createSSLCodec(false)),
        ch -> Collections.singletonList(createSSLCodec(true))
    );
  }

  @Test
  public void test() throws Exception {
    doTest(9102, null,null);
  }
  
  @Test
  public void testDeflate() throws Exception{
    CodecFactory cf = ch -> Collections.singletonList((Codec) new DeflateCodec());
    doTest(9103,cf,cf);
  }
  
  @Test
  public void testDeflateAndSSL() throws Exception{
    doTest(
        9004,
        ch -> Arrays.asList(createSSLCodec(false),new DeflateCodec()),
        ch -> Arrays.asList(createSSLCodec(true),new DeflateCodec())
    );
  }

  private void doTest(int port, final CodecFactory serverCodecFactory, final CodecFactory clientCodecFactory) throws Exception {
    final byte[] data = new byte[]{3, 4, 5, 6, 7, 8, 9, 3, 7, 9, 3};
    final ChannelHost host = ChannelHost.create();
    SocketAddress addr = new InetSocketAddress(port);
    Channel client = host.createChannel();
    host.setExceptionHandler((ch, ex) -> {
      if(ex instanceof StopException){
        ch.close();
      }else{
        throw new RuntimeException(ex);
      }
    });
    client.addConnectionListener(new ConnectionListener() {
      @Override
      public void onChannelConnected(final Channel ch) {
        log("client:connected");
        AtomicBoolean written = new AtomicBoolean(false);
        ch.write(new ByteWriteTask(data, 0, data.length) {
          @Override
          public void handleWritten(Channel channel) {
            log("client written");
            written.set(true);
          }
        });
        ch.read(new ReadTask() {
          private int counter = 0;
          @Override
          public boolean handleRead(Channel channel,IOBuffer b) {
            Assert.assertTrue(written.get());
            if(counter<3){//test prepareRead even if no new data arrived
              counter++;
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
          }

        });
      }

      @Override
      public void onChannelConnectFailed(Channel ch, Exception ex) {
        fail("client:connection failed");
      }

      @Override
      public void onChannelClosed(Channel ch) {
        log("channel closed:" + ch);
        host.stopListen();
      }

    });
    host.createServerChannel(addr, ch -> {
      if(serverCodecFactory!=null){
        for(Codec c:serverCodecFactory.createCodecs(ch)){
          ch.addCodec(c);
        }
      }
      log("server accepted:" + ch.toString());
      ch.read((self, readBuffer) -> {
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
          self.write(new ByteWriteTask(receivedData, 0, receivedData.length));
          return true;
        }
        return false;

      });
    });
    client.connect(addr);
    if(clientCodecFactory!=null){
      for(Codec c:clientCodecFactory.createCodecs(client)){
        client.addCodec(c);
      }
    }
    host.listen();
    client.close();
  }
  
  private Codec createSSLCodec(boolean clientMode){
    File keyStoreFile = new File("sslclientkeys");
    //String trustStore = "sslclientkeys";
    String pwd = "net-lib";
    try{
    SSLContext context = SSLContextUtil.createFromKeyStore(keyStoreFile, pwd);
    return new SSLCodec(context, clientMode);
    }catch(Exception ex){
      throw new RuntimeException(ex);
    }
  }

  private void log(String msg) {
    System.out.println(msg);
  }

}
