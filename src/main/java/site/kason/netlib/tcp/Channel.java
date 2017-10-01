package site.kason.netlib.tcp;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.pipeline.Pipeline;

public class Channel implements Hostable {

  Host host;

  SocketChannel socketChannel;

  int writeRequests = 0;

  int readRequests = 0;

  protected final List<WriteTask> writeTasks = new LinkedList();

  protected final List<ReadTask> readTasks = new LinkedList();

  private List<ChannelFilter> filters = new LinkedList();

  protected ConnectionHandler connectionHandler;

  private boolean closed = false;

  public ExceptionHandler DEFAULT_EXCEPTION_HANDLER = new ExceptionHandler() {
    @Override
    public void handleException(Channel ch, Exception ex) {
      Logger.getLogger(Channel.class.getName()).log(Level.SEVERE, null, ex);
      try {
        ch.close();
      } catch (IOException ex1) {
        Logger.getLogger(Channel.class.getName()).log(Level.SEVERE, null, ex1);
      }
    }

  };

  private ExceptionHandler exceptionHandler = DEFAULT_EXCEPTION_HANDLER;
  
  private final Pipeline encodePipeline = new Pipeline();
  
  private final Pipeline decodePipeline = new Pipeline();

  protected Channel(SocketChannel socketChannel, Host host) {
    this.socketChannel = socketChannel;
    this.host = host;
  }

  /**
   * connect the channel to the remote a <b>connected</b> event will be trigger
   * if connect successfully or a <b>connectFailed</b> event will be trigger
   *
   * @param remote
   * @return true if no io exception occurs
   */
  public boolean connect(SocketAddress remote) throws IOException {
    host.prepareConnect(this);
    return this.socketChannel.connect(remote);
  }

  public SocketChannel socketChannel() {
    return socketChannel;
  }

  public void close() throws IOException {
    if (this.closed) {
      return;
    }
    if (socketChannel == null) {
      return;
    }
    this.closed = true;
    try {
      if (connectionHandler != null) {
        connectionHandler.channelClosed(this);
      }
    } finally {
      socketChannel.close();
    }
  }

  @Override
  public SelectableChannel getSelectableChannel() {
    return this.socketChannel;
  }

  public ReadTask filterRead(ReadTask task) {
    for (ChannelFilter f : filters) {
      task = f.filterRead(task);
    }
    return task;
  }

  public WriteTask filterWrite(WriteTask task) {
    int count = filters.size();
    for (int i = count - 1; i >= 0; i--) {
      ChannelFilter f = filters.get(i);
      task = f.filterWrite(task);
    }
    return task;
  }

  public synchronized void write(WriteTask cb) {
    cb = filterWrite(cb);
    this.writeTasks.add(cb);
    this.prepareWrite();
  }

  public synchronized void read(ReadTask cb) {
    cb = filterRead(cb);
    this.readTasks.add(cb);
    this.prepareRead();
  }

  public synchronized void prepareWrite() {
    this.writeRequests++;
    host.prepareWrite(this);
  }

  public boolean handleWrite() {
    SocketChannel sc = this.socketChannel;
    IOBuffer out = encodePipeline.getOutBuffer();
    encodePipeline.process();
    if(out.getReadableSize()>0){
      ByteBuffer byteBuffer = ByteBuffer.wrap(out.array(),out.getReadPosition(),out.getReadableSize());
      try {
        int wlen = sc.write(byteBuffer);
        out.skip(wlen);
        return false;
      } catch (IOException ex) {
        //TODO handle ex
        throw new RuntimeException(ex);
      }
    }
    this.writeRequests--;
    List<WriteTask> writeCallbacks = this.writeTasks;
    if (writeCallbacks.size() > 0) {
      WriteTask cb = writeCallbacks.get(0);
      boolean writeFinished;
      try {
        writeFinished = cb.handleWrite(encodePipeline.getInBuffer());
      } catch (Exception ex) {
        writeFinished = false;
        exceptionHandler.handleException(this, ex);
      }
      if (writeFinished) {
        writeCallbacks.remove(0);
      }
    }
    return writeRequests <= 0 && out.getReadableSize()<=0;
  }

  public synchronized void prepareRead() {
    this.readRequests++;
    host.prepareRead(this);
  }

  public boolean handleRead() {
    SocketChannel sc = this.socketChannel;
    IOBuffer in = decodePipeline.getInBuffer();
    IOBuffer out = decodePipeline.getOutBuffer();
    ByteBuffer byteBuffer = ByteBuffer.wrap(in.array(), in.getWritePosition(), in.getWritableSize());
    try {
      int rlen = sc.read(byteBuffer);
      if(rlen==-1){
        this.close();
        return true;
      }
      in.setWritePosition(in.getWritePosition()+rlen);
    } catch (IOException ex) {
      //TODO handle ex
      throw new RuntimeException(ex);
    }
    decodePipeline.process();
    if(out.getReadableSize()<=0){
      return false;
    }
    this.readRequests--;
    List<ReadTask> readCallbacks = readTasks;
    if (readCallbacks.size() > 0) {
      ReadTask cb = readCallbacks.get(0);
      boolean readFinished;
      try {
        readFinished = cb.handleRead(out);
      } catch (Exception ex) {
        readFinished = false;
        exceptionHandler.handleException(this, ex);
      }
      if (readFinished) {
        readCallbacks.remove(0);
      }
    }
    return this.readRequests <= 0;
  }

  public synchronized void prepareConnect() {
    host.prepareConnect(this);
  }

  public ConnectionHandler getConnectionHandler() {
    return connectionHandler;
  }

  public void setConnectionHandler(ConnectionHandler connectionHandler) {
    this.connectionHandler = connectionHandler;
  }

  public void installFilter(ChannelFilter filter) {
    this.filters.add(filter);
    filter.installed(this);
  }

  public void setExceptionHandler(ExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public String toString() {
    String la = "";
    String ra = "";
    if (socketChannel != null) {
      try {
        la = socketChannel.getLocalAddress().toString();
        ra = socketChannel.getRemoteAddress().toString();
      } catch (IOException ex) {

      }
    }
    return String.format("[local=%s,remote=%s]", la, ra);
  }

  public int getWriteTaskCount() {
    return this.writeTasks.size();
  }

  public int getReadTaskCount() {
    return this.readTasks.size();
  }
  
  public void addCodec(Codec codec){
    if(codec.hasEncoder()){
      this.encodePipeline.addProcessor(codec.getEncoder());
    }
    if(codec.hasDecoder()){
      this.decodePipeline.addProcessor(codec.getDecoder());
    }
  }

}
