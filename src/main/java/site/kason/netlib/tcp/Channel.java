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

  private static final int READ_MODE_READ_AVAILABLE = 0;

  private static final int READ_MODE_READ_MORE = 1;

  private static int ID_COUNTER = 0;

  private final static Logger LOG = Logger.getLogger(Channel.class);

  private Host host;

  private int id;

  private int readMode;

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
    host.requestConnect(this);
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
    this.requestWrite();
  }

  public synchronized void read(ReadTask cb) {
    cb = filterRead(cb);
    this.readTasks.add(cb);
    this.requestRead();
  }

  public synchronized void requestWrite() {
    this.pauseWritePending = false;
    host.registerWrite(this);
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
      host.unregisterWrite(this);
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

  public synchronized void requestRead() {
    this._requestRead(READ_MODE_READ_AVAILABLE);
  }

  public synchronized void requestReadMore() {
    this._requestRead(READ_MODE_READ_MORE);
  }

  public synchronized void pauseRead() {
    host.unregisterRead(this);
  }

  /**
   *
   * @return true if no more data to handle
   */
  @SneakyThrows
  protected synchronized boolean handleRead() {
    LOG.debug("%s: handle read", this);
    SocketChannel sc = this.socketChannel;
    IOBuffer in = decodePipeline.getInBuffer();
    IOBuffer out = decodePipeline.getOutBuffer();
    int oldReadableSize = out.getReadableSize();
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
    int newReadableSize = out.getReadableSize();
    if (!isMatchReadMode(oldReadableSize, newReadableSize)) {
      LOG.debug("%s: buffer change not match read mode:%d -> %d", this, oldReadableSize, newReadableSize);
      if (this.readState == RS_COMPLETE_PENDING) {
        this.readState = RS_COMPLETED;
        LOG.debug("%s: read state: RS_COMPLETED", this);
        host.unregisterRead(this);
        this.handleReadCompleted();
      }
      return true;
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
    }
    if (readCallbacks.isEmpty()) {
      LOG.debug("%s: no more read tasks", this);
      host.unregisterRead(this);
      return true;
    }
    return false;
  }

  public synchronized void requestConnect() {
    host.requestConnect(this);
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

  private synchronized void _requestRead(int readMode) {
    if (this.readState == RS_COMPLETED) {
      LOG.debug("%s: read completed, read request is ignored");
      return;
    }
    this.readMode = readMode;
    host.registerRead(this);
  }

  private boolean isMatchReadMode(int oldReadableSize, int newReadableSize) {
    switch (readMode) {
      case READ_MODE_READ_AVAILABLE:
        return newReadableSize > 0;
      case READ_MODE_READ_MORE:
        return newReadableSize > oldReadableSize;
      default:
        throw new RuntimeException("unknown read mode:" + readMode);
    }
  }

}
