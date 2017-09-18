package site.kason.netlib.tcp;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class ChannelHost implements Host {

  Selector selector;

  private boolean cancelled = false;

  private HashMap<SelectableChannel, Hostable> channels = new HashMap();

  private HashMap<Hostable, SelectableChannel> socketChannels = new HashMap();

  //private ByteBuffer readBuffer = ByteBuffer.allocate(40960);
  public static ChannelHost create() throws IOException {
    ChannelHost host = new ChannelHost();
    return host;
  }

  @Override
  public void prepareConnect(Channel ch) {
    this.interest(ch, SelectionKey.OP_CONNECT, true);
  }

  @Override
  public void prepareWrite(Channel ch) {
    this.interest(ch, SelectionKey.OP_WRITE, true);
  }

  @Override
  public void prepareRead(Channel ch) {
    this.interest(ch, SelectionKey.OP_READ, true);
  }

  protected ChannelHost() throws IOException {
    selector = Selector.open();
  }

  private void interest(Channel ch, int key, boolean interest) {
    SocketChannel sc = ch.socketChannel();
    SelectionKey selectionKey = sc.keyFor(selector);
    int ops = selectionKey.interestOps();
    if (interest) {
      ops |= key;
    } else {
      ops &= ~key;
    }
    selectionKey.interestOps(ops);
    selector.wakeup();
  }

  public void stopListen() {
    selector.wakeup();
    cancelled = true;
  }

  private void onSocketChannelKey(SelectionKey key) {
    SocketChannel sc = (SocketChannel) key.channel();
    Channel ch = (Channel) channels.get(sc);
    if (key.isReadable()) {
      if (ch.handleRead()) {
        this.interest(ch, SelectionKey.OP_READ, false);
      }
    }
    if (key.isWritable()) {
      if (ch.handleWrite()) {
        this.interest(ch, SelectionKey.OP_WRITE, false);
      }
    }
    if (key.isConnectable()) {
      this.interest(ch, SelectionKey.OP_CONNECT, false);
      ConnectionHandler eh = ch.getConnectionHandler();
      try {
        if (sc.isConnectionPending()) {
          sc.finishConnect();
          if (eh != null) {
            eh.channelConnected(ch);
          }
        }
      } catch (IOException ex) {
        if (eh != null) {
          eh.channelConnectFailed(ch, ex);
        }
      }
    }
  }

  public void listen() {
    for (;;) {
      try {
        int readyCount = selector.select();
        if (cancelled) {
          return;
        }
//        if (readyCount == 0) {
//          throw new RuntimeException("Bug!Zero channels selected!");
//        }
        //System.out.println(readyCount+" channels ready!");
      } catch (IOException e1) {
        throw new RuntimeException(e1);
      }
      Set<SelectionKey> selectionKeys = selector.selectedKeys();
      Iterator<SelectionKey> iter = selectionKeys.iterator();
      while (iter.hasNext()) {
        SelectionKey key = iter.next();
        iter.remove();
        if (!key.isValid()) {
          continue;
        }

        if (key.isAcceptable()) {
          ServerSocketChannel nssc = (ServerSocketChannel) key.channel();
          try {
            SocketChannel sc = nssc.accept();
            if (sc == null) {
              continue;
            }
            ((ServerChannel) channels.get(nssc)).accept(sc);
          } catch (IOException e) {
            //TODO handle exception
            throw new RuntimeException(e);
          }
        } else {
          this.onSocketChannelKey(key);
        }
      }
    }
  }

  private void hostChannel(Hostable channel) throws ClosedChannelException {
    SelectableChannel sc = channel.getSelectableChannel();
    channels.put(sc, channel);
    socketChannels.put(channel, sc);
    if (sc instanceof ServerSocketChannel) {
      sc.register(selector, SelectionKey.OP_ACCEPT);
    } else if (sc instanceof SocketChannel) {
      if (!((SocketChannel) sc).isConnected()) {
        sc.register(selector, SelectionKey.OP_CONNECT);
      } else {
        sc.register(selector, 0);
      }
    }
  }

  @Override
  public Channel createChannel() throws IOException {
    SocketChannel sc = SocketChannel.open();
    sc.configureBlocking(false);
    return createChannel(sc);
  }

  @Override
  public Channel createChannel(SocketChannel sc) throws ClosedChannelException {
    Channel ch = new Channel(sc, this);
    this.hostChannel(ch);
    return ch;
  }

  @Override
  public ServerChannel createServerChannel(SocketAddress endpoint, AcceptHandler acceptHandler) throws IOException {
    ServerChannel sc = ServerChannel.create(this, acceptHandler);
    sc.bind(endpoint);
    this.hostChannel(sc);
    return sc;
  }

}
