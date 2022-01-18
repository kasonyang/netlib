package site.kason.netlib.tcp;

import lombok.SneakyThrows;
import site.kason.netlib.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.function.Supplier;

public class ChannelHost implements Host {

  private static Logger LOG = Logger.getLogger(ChannelHost.class);

  Selector selector;

  private ExceptionHandler exceptionHandler = (ch, ex) -> {
    LOG.error(ex);
    try {
      ch.close();
    } catch (Throwable closeEx){
      LOG.error(closeEx);
    }
  };

  private boolean cancelled = false;

  private HashMap<SelectableChannel, Hostable> channels = new HashMap<>();

  private HashMap<Hostable, SelectableChannel> socketChannels = new HashMap<>();

  private Set<Channel> readQueue = new LinkedHashSet<>();

  private Set<Channel> writeQueue = new LinkedHashSet<>();

  //private ByteBuffer readBuffer = ByteBuffer.allocate(40960);
  public static ChannelHost create() throws IOException {
    return new ChannelHost();
  }

  @Override
  public void requestConnect(Channel ch) {
    this.interest(ch, SelectionKey.OP_CONNECT, true);
  }

  @Override
  public void registerWrite(Channel ch) {
    LOG.debug("%s: continue write", ch);
    this.interest(ch, SelectionKey.OP_WRITE, true);
  }

  @Override
  public void unregisterWrite(Channel ch) {
    LOG.debug("%s: pause write", ch);
    this.interest(ch, SelectionKey.OP_WRITE, false);
  }

  @Override
  public boolean isWriteRegistered(Channel ch) {
    return isInterest(ch, SelectionKey.OP_WRITE);
  }

  @Override
  public void registerRead(Channel ch) {
    LOG.debug("%s: continue read", ch);
    if (!this.isInterest(ch, SelectionKey.OP_READ)) {
      this.readQueue.add(ch);
      selector.wakeup();
    }
    this.interest(ch, SelectionKey.OP_READ, true);
  }

  @Override
  public void unregisterRead(Channel ch) {
    LOG.debug("%s: unregister read", ch);
    this.interest(ch, SelectionKey.OP_READ, false);
  }

  @Override
  public boolean isReadRegistered(Channel ch) {
    return isInterest(ch, SelectionKey.OP_READ);
  }

  protected ChannelHost() throws IOException {
    selector = Selector.open();
  }

  public void setExceptionHandler(ExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  private boolean isInterest(Channel ch, int key) {
    SocketChannel sc = ch.socketChannel();
    SelectionKey selectionKey = sc.keyFor(selector);
    try{
      int ops = selectionKey.interestOps();
      return (ops & key) != 0;
    }catch(CancelledKeyException ex){
      //ignore it
      return false;
    }
  }

  private void interest(Channel ch, int key, boolean interest) {
    SocketChannel sc = ch.socketChannel();
    SelectionKey selectionKey = sc.keyFor(selector);
    if (selectionKey == null) {
      return;
    }
    try{
      int ops = selectionKey.interestOps();
      if (interest) {
        ops |= key;
      } else {
        ops &= ~key;
      }
      selectionKey.interestOps(ops);
      selector.wakeup();
    }catch(CancelledKeyException ex){
      //ignore it
    }
    
  }

  public void stopListen() {
    selector.wakeup();
    cancelled = true;
  }

  private void onSocketChannelKey(SelectionKey key) {
    SocketChannel sc = (SocketChannel) key.channel();
    Channel ch = (Channel) channels.get(sc);
    if (key.isReadable()) {
      readQueue.add(ch);
    }
    if (key.isWritable()) {
      writeQueue.add(ch);
    }
    if (key.isConnectable()) {
      this.interest(ch, SelectionKey.OP_CONNECT, false);
      try {
        if (sc.isConnectionPending()) {
          sc.finishConnect();
          execChannelBusiness(ch, ch::handleConnected);
        }
      } catch (IOException ex) {
        execChannelBusiness(ch, () -> ch.handleConnectFailed(ex));
      }
    }
  }

  @SneakyThrows
  public void listen() {
    for (;;) {
      selector.select();
      if (cancelled) {
        return;
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
          SocketChannel sc = nssc.accept();
          if (sc == null) {
            continue;
          }
          sc.configureBlocking(false);
          Channel ch = createChannel(sc);
          ServerChannel serverChannel = (ServerChannel) channels.get(nssc);
          execChannelBusiness(ch, () -> serverChannel.accepted(ch));
        } else {
          try{
            this.onSocketChannelKey(key);
          }catch(CancelledKeyException ex){
            LOG.debug("%s: key cancelled", key.channel());
          }
        }
      }
      List<Channel> readList = new ArrayList<>(this.readQueue);
      this.readQueue.clear();
      for (Channel r : readList) {
        execChannelBusiness(r, r::handleRead);
      }
      List<Channel> writeList = new ArrayList<>(this.writeQueue);
      this.writeQueue.clear();
      for (Channel w : writeList) {
        execChannelBusiness(w, w::handleWrite);
      }
      if (!this.readQueue.isEmpty()) {
        selector.wakeup();
      }
    }
  }

  private boolean execChannelBusiness(Channel channel, Runnable businessCallback) {
    try {
      businessCallback.run();
      return true;
    } catch (Throwable ex) {
      exceptionHandler.handleException(channel, ex);
      return false;
    }
  }

  private <T> ExecChannelResult<T> execChannelBusiness(Channel channel, Supplier<T> businessCallback) {
    try {
      return new ExecChannelResult<>(true, businessCallback.get());
    } catch (Throwable ex) {
      exceptionHandler.handleException(channel, ex);
      return new ExecChannelResult<>(false, null);
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
  public void closeChannel(Hostable channel){
    SelectableChannel sc = channel.getSelectableChannel();
    channels.remove(sc);
    socketChannels.remove(channel);
    sc.keyFor(selector).cancel();
    LOG.debug("%s closed", channel);
  }

  @Override
  @SneakyThrows
  public Channel createChannel() {
    SocketChannel sc = SocketChannel.open();
    sc.configureBlocking(false);
    return createChannel(sc);
  }

  @Override
  @SneakyThrows
  public Channel createChannel(SocketChannel sc){
    Channel ch = new Channel(sc, this);
    this.hostChannel(ch);
    return ch;
  }

  @Override
  @SneakyThrows
  public ServerChannel createServerChannel(SocketAddress endpoint, AcceptHandler acceptHandler) {
    ServerChannel sc = ServerChannel.create(this, acceptHandler);
    sc.bind(endpoint);
    this.hostChannel(sc);
    return sc;
  }

  public ServerChannel createServerChannel(String host, int port, AcceptHandler acceptHandler) {
    return createServerChannel(new InetSocketAddress(host, port), acceptHandler);
  }

  public ServerChannel createServerChannel(int port, AcceptHandler acceptHandler) {
    return createServerChannel(new InetSocketAddress(port), acceptHandler);
  }

  private static class ExecChannelResult<T> {
    boolean isSuccessful;
    T value;
    public ExecChannelResult(boolean isSuccessful, T value) {
      this.isSuccessful = isSuccessful;
      this.value = value;
    }
  }

}
