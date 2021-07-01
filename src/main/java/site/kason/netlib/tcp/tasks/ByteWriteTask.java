package site.kason.netlib.tcp.tasks;

import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.WriteTask;

/**
 *
 * @author Kason Yang
 */
public class ByteWriteTask implements WriteTask {

  private final byte[] data;

  private final int lastOffset;

  private int offset;

  private final Runnable finishCallback;

  public ByteWriteTask(byte[] data) {
    this(data, null);
  }

  public ByteWriteTask(byte[] data, Runnable finishCallback) {
    this(data,0,data.length, finishCallback);
  }

  public ByteWriteTask(byte[] data, int offset, int length) {
    this(data, offset, length, null);
  }

  public ByteWriteTask(byte[] data, int offset, int length, Runnable finishCallback) {
    this.data = data;
    this.offset = offset;
    this.lastOffset = offset + length - 1;
    this.finishCallback = finishCallback;
  }

  @Override
  public boolean handleWrite(Channel ch,IOBuffer buffer) throws Exception {
    int remaining = lastOffset - offset + 1;
    if (remaining > 0) {
      int maxSize = Math.min(remaining, buffer.getWritableSize());
      buffer.push(data, offset, maxSize);
      offset+=maxSize;
    }
    boolean finished = offset > lastOffset;
    if (finished && this.finishCallback != null) {
      this.finishCallback.run();
    }
//    if(!finished){
//      ch.prepareWrite();
//    }
    return finished;
  }

}
