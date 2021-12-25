package site.kason.netlib.tcp;

import lombok.SneakyThrows;
import site.kason.netlib.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.*;

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
  
  private List<Channel> readRequiredList = new LinkedList<>();
  
  private List<Channel> writeRequiredList = new LinkedList<>();

  //private ByteBuffer readBuffer = ByteBuffer.allocate(40960);
  public static ChannelHost create() throws IOException {
    return new ChannelHost();
  }

  @Override
  public void prepareConnect(Channel ch) {
    this.interest(ch, SelectionKey.OP_CONNECT, true);
  }

  @Override
  public void continueWrite(Channel ch) {
    LOG.debug("%s: continue write", ch);
    this.interest(ch, SelectionKey.OP_WRITE, true);
  }

  @Override
  public void pauseWrite(Channel ch) {
    LOG.debug("%s: pause write", ch);
    this.interest(ch, SelectionKey.OP_WRITE, false);
  }

  @Override
  public boolean isWritePaused(Channel ch) {
    return !isInterest(ch, SelectionKey.OP_WRITE);
  }

  @Override
  public void continueRead(Channel ch) {
    LOG.debug("%s: continue read", ch);
    if(ch.isReadable()){
      this.readRequiredList.add(ch);
      selector.wakeup();
    }else{
      this.interest(ch, SelectionKey.OP_READ, true);
    }
  }

  @Override
  public void pauseRead(Channel ch) {
    LOG.debug("%s: pause read", ch);
    this.interest(ch, SelectionKey.OP_READ, false);
  }

  @Override
  public boolean isReadPaused(Channel ch) {
    return !isInterest(ch, SelectionKey.OP_READ);
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
      if (!execChannelBusiness(ch, ch::handleRead)) {
        return;
      }
    }
    if (key.isWritable()) {
      if (!execChannelBusiness(ch, ch::handleWrite)) {
        return;
      }
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
      List<Channel> readList = new ArrayList<>(this.readRequiredList);
      this.readRequiredList.clear();
      for (Channel r : readList) {
        execChannelBusiness(r, r::handleRead);
      }
      List<Channel> writeList = new ArrayList<>(this.writeRequiredList);
      this.writeRequiredList.clear();
      for (Channel w : writeList) {
        execChannelBusiness(w, w::handleWrite);
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
            
          }
        }
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

}
