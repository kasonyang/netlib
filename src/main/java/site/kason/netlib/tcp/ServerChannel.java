package site.kason.netlib.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ServerChannel implements Hostable {

  private ServerSocketChannel ssc;

  private final Host host;

  private AcceptHandler acceptHandler;

  public static ServerChannel create(Host host, AcceptHandler channelHandler) throws IOException {
    ServerSocketChannel ssc = ServerSocketChannel.open();
    ssc.configureBlocking(false);
    ServerChannel sc = new ServerChannel(host, ssc, channelHandler);
    return sc;
  }

  public ServerChannel(Host host, ServerSocketChannel ssc, AcceptHandler channelHandler) {
    this.ssc = ssc;
    this.acceptHandler = channelHandler;
    this.host = host;
  }

  public ServerSocketChannel serverSocketChannel() {
    return ssc;
  }

  public void bind(SocketAddress endpoint) throws IOException {
    ServerSocket socket = ssc.socket();
    socket.bind(endpoint);
  }

  @Override
  public SelectableChannel getSelectableChannel() {
    return this.ssc;
  }

  public void accepted(Channel ch) {
    if (this.acceptHandler != null) {
      this.acceptHandler.accepted(ch);
    }
  }

}
