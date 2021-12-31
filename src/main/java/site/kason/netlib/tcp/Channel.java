package site.kason.netlib.tcp;

import lombok.SneakyThrows;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.pipeline.CodecInitProgress;
import site.kason.netlib.tcp.pipeline.Pipeline;
import site.kason.netlib.tcp.pipeline.Processor;
import site.kason.netlib.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Channel implements Hostable {

  private static int ID_COUNTER = 0;

  private final static Logger LOG = Logger.getLogger(Channel.class);

  private Host host;

  private int id;

  private SocketChannel socketChannel;

  private final List<WriteTask> writeTasks = new LinkedList<>();

  private final List<ReadTask> readTasks = new LinkedList<>();

  private List<ChannelFilter> filters = new LinkedList<>();

  private final Queue<Codec> codecInitQueue = new LinkedList<>();

  private CodecInitProgress codecInitProgress;

  private List<ConnectionListener> connectionListener = new LinkedList<>();

  private boolean closed = false;

  private final static int RS_READING = 0;
  private final static int RS_COMPLETE_PENDING = 1;
  private final static int RS_COMPLETED = 2;
  /**
   * read state,0=reading,1=completePending,2=completed
   */
  private int readState = 0;

  private boolean pauseWritePending = false;

  private WriteTask writtenTask;

  private final Pipeline encodePipeline = new Pipeline();
  
  private final Pipeline decodePipeline = new Pipeline();

  protected Channel(SocketChannel socketChannel, Host host) {
    this.socketChannel = socketChannel;
    this.host = host;
    this.id = ID_COUNTER++;
  }

  /**
   * connect the channel to the remote a <b>connected</b> event will be trigger
   * if connect successfully or a <b>connectFailed</b> event will be trigger
   *
   * @param remote the remote address to connect
   * @return true if no io exception occurs
   */
  @SneakyThrows
  public boolean connect(SocketAddress remote) {
    LOG.debug("%s: connecting %s", this, remote);
    host.prepareConnect(this);
    return this.socketChannel.connect(remote);
  }

  public boolean connect(String host, int port){
    return connect(new InetSocketAddress(host, port));
  }

  public SocketChannel socketChannel() {
    return socketChannel;
  }

  @SneakyThrows
  public void close(){
    if (this.closed) {
      return;
    }
    if (socketChannel == null) {
      return;
    }
    this.closed = true;
    try {
      for (ConnectionListener cl : connectionListener) {
        cl.onChannelClosed(this);
      }
    } finally {
      host.closeChannel(this);
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
    this.continueWrite();
  }

  public synchronized void read(ReadTask cb) {
    cb = filterRead(cb);
    this.readTasks.add(cb);
    this.continueRead();
  }

  public synchronized void continueWrite() {
    this.pauseWritePending = false;
    host.continueWrite(this);
  }

  public synchronized void pauseWrite() {
    this.pauseWritePending = true;
  }

  @SneakyThrows
  protected synchronized void handleWrite() {
    SocketChannel sc = this.socketChannel;
    IOBuffer out = encodePipeline.getOutBuffer();
    encodePipeline.process();
    if (out.getReadableSize() > 0) {
      ByteBuffer byteBuffer = ByteBuffer.wrap(out.array(), out.getReadPosition(), out.getReadableSize());
      int wlen = sc.write(byteBuffer);
      out.moveReadPosition(wlen);
      return;
    }
    if (writtenTask != null) {
      WriteTask wt = writtenTask;
      writtenTask = null;
      LOG.debug("%s: calling handleWritten: %s", this, wt);
      wt.handleWritten(this);
    }
    if (pauseWritePending) {
      pauseWritePending = false;
      host.pauseWrite(this);
      return;
    }
    List<WriteTask> writeCallbacks = this.writeTasks;
    if (writeCallbacks.isEmpty()) {
      LOG.debug("%s: no more write tasks", this);
      pauseWrite();
      return;
    }
    WriteTask cb = writeCallbacks.get(0);
    LOG.debug("%s: calling write task %s", this, cb);
    boolean writeFinished = cb.handleWrite(this, encodePipeline.getInBuffer());
    if (writeFinished) {
      LOG.debug("%s: write task finished: %s", this, cb);
      writtenTask = writeCallbacks.remove(0);
    }
  }

  public synchronized void continueRead() {
    host.continueRead(this);
  }

  public synchronized void pauseRead() {
    host.pauseRead(this);
  }

  @SneakyThrows
  protected synchronized void handleRead() {
    LOG.debug("%s: handle read", this);
    SocketChannel sc = this.socketChannel;
    IOBuffer in = decodePipeline.getInBuffer();
    IOBuffer out = decodePipeline.getOutBuffer();
    ByteBuffer byteBuffer = ByteBuffer.wrap(in.array(), in.getWritePosition(), in.getWritableSize());
    if (this.readState == RS_READING) {
      int rlen = sc.read(byteBuffer);
      LOG.debug("%s: receive %d bytes",this, rlen);
      if (rlen == -1) {
        this.readState = RS_COMPLETE_PENDING;
      } else if (rlen > 0) {
        in.setWritePosition(in.getWritePosition() + rlen);
      }
    }
    decodePipeline.process();
    if (out.getReadableSize() <= 0) {//no data for read
      LOG.debug("%s: readable is 0", this);
      if (this.readState == RS_COMPLETE_PENDING) {
        this.readState = RS_COMPLETED;
        LOG.debug("%s: read state: RS_COMPLETED", this);
        pauseRead();
        this.handleReadCompleted();
      }
      return;
    }
    List<ReadTask> readCallbacks = readTasks;
    if (readCallbacks.size() > 0) {
      ReadTask cb = readCallbacks.get(0);
      LOG.debug("%s: calling read task %s", this, cb);
      boolean readFinished = cb.handleRead(this, out);
      if (readFinished) {
        LOG.debug("%s: read task finished: %s", this, cb);
        readCallbacks.remove(0);
      }
      if (readCallbacks.isEmpty()) {
        LOG.debug("%s: no more read tasks", this);
        pauseRead();
      } else if (out.getReadableSize() > 0 && !host.isReadPaused(this)) {
        // call continueRead to wakeup selector
        continueRead();
      }
    } else {
      LOG.debug("%s: no read tasks", this);
    }
  }

  public synchronized void prepareConnect() {
    host.prepareConnect(this);
  }

  public void addConnectionListener(ConnectionListener connectionListener) {
    this.connectionListener.add(connectionListener);
  }

  public void installFilter(ChannelFilter filter) {
    this.filters.add(filter);
    filter.installed(this);
  }

  @Override
  public String toString() {
    return "Channel#" + id;
  }

  public int getWriteTaskCount() {
    return this.writeTasks.size();
  }

  public int getReadTaskCount() {
    return this.readTasks.size();
  }
  
  public void addCodec(Codec codec){
    Processor encoder = codec.getEncoder();
    Processor decoder = codec.getDecoder();
    if (encoder != null) {
      this.encodePipeline.addProcessor(encoder);
    }
    if (decoder != null) {
      this.decodePipeline.addProcessor(0, decoder);
    }
  }
  
  public boolean isReadable(){
    return this.decodePipeline.getOutBuffer().getReadableSize()>0;
  }
  
  public boolean isWritable(){
    return this.encodePipeline.getOutBuffer().getReadableSize()>0;
  }

  protected void handleConnected() {
    LOG.debug("channel connected: %s %s", this, socketChannel);
    for (ConnectionListener cl : connectionListener) {
      cl.onChannelConnected(this);
    }
  }

  protected void handleConnectFailed(IOException ex) {
    for (ConnectionListener cl : connectionListener) {
      cl.onChannelConnectFailed(this, ex);
    }
  }

  protected void handleReadCompleted() {
    for (ConnectionListener cl : connectionListener) {
      cl.onReadCompleted(this);
    }
  }

}
