package site.kason.netlib.tcp;

import lombok.SneakyThrows;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.pipeline.CodecInitProgress;
import site.kason.netlib.tcp.pipeline.Pipeline;
import site.kason.netlib.tcp.pipeline.Processor;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Channel implements Hostable {

  Host host;

  SocketChannel socketChannel;

  protected final List<WriteTask> writeTasks = new LinkedList<WriteTask>();

  protected final List<ReadTask> readTasks = new LinkedList<ReadTask>();

  private List<ChannelFilter> filters = new LinkedList<ChannelFilter>();

  private final Queue<Codec> codecInitQueue = new LinkedList<Codec>();

  private CodecInitProgress codecInitProgress;

  protected List<ConnectionListener> connectionListener = new LinkedList<>();

  private boolean closed = false;
  
  private boolean closePending = false;

  private boolean pauseWritePending = false;

  private WriteTask writtenTask;

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
   * @param remote the remote address to connect
   * @return true if no io exception occurs
   * @throws IOException if some i/o error occurs
   */
  public boolean connect(SocketAddress remote) throws IOException {
    host.prepareConnect(this);
    return this.socketChannel.connect(remote);
  }

  public SocketChannel socketChannel() {
    return socketChannel;
  }

  @SneakyThrows
  public void close(){
    this.closePending = false;
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
  protected void handleWrite() {
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
      wt.handleWritten(this);
    }
    if (pauseWritePending) {
      pauseWritePending = false;
      host.pauseWrite(this);
      return;
    }
    List<WriteTask> writeCallbacks = this.writeTasks;
    if (writeCallbacks.isEmpty()) {
      pauseWrite();
      return;
    }
    WriteTask cb = writeCallbacks.get(0);
    boolean writeFinished = cb.handleWrite(this, encodePipeline.getInBuffer());
    if (writeFinished) {
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
  protected void handleRead() {
    SocketChannel sc = this.socketChannel;
    IOBuffer in = decodePipeline.getInBuffer();
    IOBuffer out = decodePipeline.getOutBuffer();
    ByteBuffer byteBuffer = ByteBuffer.wrap(in.array(), in.getWritePosition(), in.getWritableSize());
    int rlen = sc.read(byteBuffer);
    if (rlen == -1) {
      this.closePending = true;
    } else if (rlen > 0) {
      in.setWritePosition(in.getWritePosition() + rlen);
    }
    decodePipeline.process();
    if (out.getReadableSize() <= 0) {//no data for read
      if (this.closePending) {
        this.close();
      }
      return;
    }
    List<ReadTask> readCallbacks = readTasks;
    if (readCallbacks.size() > 0) {
      ReadTask cb = readCallbacks.get(0);
      boolean readFinished = cb.handleRead(this, out);
      if (readFinished) {
        readCallbacks.remove(0);
      }
      if (readCallbacks.isEmpty()) {
        pauseRead();
      } else {
        continueRead();
      }
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
    if (socketChannel != null) {
      return String.valueOf(socketChannel.socket());
    }
    return "";
  }

  public int getWriteTaskCount() {
    return this.writeTasks.size();
  }

  public int getReadTaskCount() {
    return this.readTasks.size();
  }
  
  public void addCodec(Codec codec){
    codecInitQueue.add(codec);
    initCodec();
  }
  
  public boolean isReadable(){
    return this.decodePipeline.getOutBuffer().getReadableSize()>0;
  }
  
  public boolean isWritable(){
    return this.encodePipeline.getOutBuffer().getReadableSize()>0;
  }

  public void handleConnected() {
    for (ConnectionListener cl : connectionListener) {
      cl.onChannelConnected(this);
    }
  }

  public void handleConnectFailed(IOException ex) {
    for (ConnectionListener cl : connectionListener) {
      cl.onChannelConnectFailed(this, ex);
    }
  }

  private void initCodec() {
    if (codecInitProgress != null) {
      return;
    }
    final Channel channel = this;
    codecInitProgress = new CodecInitProgress() {
      private Codec currentCodec;
      @Override
      public void done() {
        if (currentCodec != null) {
          Processor encoder = currentCodec.getEncoder();
          if (encoder != null) {
            encodePipeline.addProcessor(encoder);
          }
          Processor decoder = currentCodec.getDecoder();
          if (decoder != null) {
            decodePipeline.addProcessor(0, decoder);
          }
          currentCodec = null;
        }
        if (codecInitQueue.isEmpty()) {
          channel.codecInitProgress = null;
          continueRead();
          continueWrite();
          return;
        }
        currentCodec = codecInitQueue.poll();
        currentCodec.init(channel, this);
      }
    };
    codecInitProgress.done();
  }

}
