package test.site.kason.netlib;

import kalang.lang.AsyncThread;
import kalang.lang.Completable;
import kalang.lang.Ref;
import lombok.SneakyThrows;
import org.junit.Test;
import site.kason.netlib.Channel;
import site.kason.netlib.ChannelHost;
import site.kason.netlib.Plugin;
import site.kason.netlib.ServerChannel;
import site.kason.netlib.plugin.DeflatePlugin;
import site.kason.netlib.plugin.SSLPlugin;
import site.kason.netlib.util.SSLContextUtil;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

public class ChannelHostTest {

  @Test(timeout = 5000)
  public void testSSL() throws Exception {
    doTest(
        9101,
        new Plugin[]{createSSLPlugin(false)},
        new Plugin[]{createSSLPlugin(true)}
    );
  }

  @Test(timeout = 5000)
  public void test() {
    doTest(9102, null,null);
  }
  
  @Test(timeout = 5000)
  public void testDeflate() {
    doTest(9103,new Plugin[]{new DeflatePlugin()}, new Plugin[]{new DeflatePlugin()});
  }
  
  @Test(timeout = 5000)
  public void testDeflateAndSSL() {
    doTest(
        9004,
        new Plugin[]{createSSLPlugin(false), new DeflatePlugin()},
        new Plugin[]{createSSLPlugin(true), new DeflatePlugin()}
    );
  }

  @SneakyThrows
  private void doTest(int port, final Plugin[] serverPlugins, final Plugin[] clientPlugins) {
//    final byte[] data = new byte[]{3, 4, 5, 6, 7, 8, 9, 3, 7, 9, 3};
    final byte[] data = createData();
    System.out.println("data length:" + data.length);
    final ChannelHost host = ChannelHost.create();
    SocketAddress addr = new InetSocketAddress(port);
    AsyncThread asyncThread = AsyncThread.create();
    Ref<Throwable> error = new Ref<>(null);
    asyncThread.submitTask(() -> {
      host.createServerChannel(addr).onCompleted(serverChannel -> {
        serverChannel.accept().onCompleted(ch -> {
          if (serverPlugins != null) {
            ch.installPlugin(serverPlugins);
          }
          log("server accepted:" + ch.toString());
          byte[] readBuff = new byte[data.length];
          return ch.getReader().readFully(readBuff).onCompleted(v -> {
            log("server read");
            assertArrayEquals(data, readBuff);
            return ch.getWriter().writeFully(readBuff);
          }).onFinally(() -> {
            log("server written");
          }).onFailed(e -> {
            error.set(e);
            host.stopListen();
          });
        });
      });
    });

    asyncThread.submitTask(() -> {
      host.createChannel().onCompleted(client -> {
        if (clientPlugins != null) {
          client.installPlugin(clientPlugins);
        }
        client.connect(addr).onCompleted(vv -> {
          log("client:connected");
          final byte[] readBuff = new byte[data.length];
          return client.getWriter().writeFully(data).onCompleted(v -> {
            log("client written");
            return client.getReader().readFully(readBuff);
          }).onCompleted(v -> {
            log("client read");
            assertArrayEquals(data, readBuff);
            log("all ok");
            return Completable.resolve(null);
          }).onFailed(error::set).onFinally(() -> {
            client.close();
            host.stopListen();
          });
        });
      });
    });
    host.listen();
    asyncThread.interrupt();
    if (error.get() != null) {
      throw error.get();
    }
  }
  
  private SSLPlugin createSSLPlugin(boolean clientMode){
    File keyStoreFile = new File("sslclientkeys");
    //String trustStore = "sslclientkeys";
    String pwd = "net-lib";
    try{
    SSLContext context = SSLContextUtil.createFromKeyStore(keyStoreFile, pwd);
    return new SSLPlugin(context, clientMode);
    }catch(Exception ex){
      throw new RuntimeException(ex);
    }
  }

  private void log(String msg) {
    System.out.println(msg);
  }

  private byte[] createData() {
    Random random = new Random(System.currentTimeMillis());
    int length = random.nextInt(102400) + 4096;
    byte[] data = new byte[length];
    random.nextBytes(data);
    return data;
  }

}
