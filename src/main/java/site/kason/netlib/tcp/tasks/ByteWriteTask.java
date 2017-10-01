package site.kason.netlib.tcp.tasks;

import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.WriteTask;

/**
 *
 * @author Kason Yang
 */
public class ByteWriteTask implements WriteTask {

  private final byte[] data;

  private int lastOffset;

  private int offset;

  public ByteWriteTask(byte[] data, int offset, int length) {
    this.data = data;
    this.offset = offset;
    this.lastOffset = offset + length - 1;
  }

  @Override
  public boolean handleWrite(IOBuffer buffer) throws Exception {
    int remaining = lastOffset - offset + 1;
    if (remaining > 0) {
      int maxSize = Math.min(remaining, buffer.getWritableSize());
      buffer.push(data, offset, maxSize);
      offset+=maxSize;
    }
    return offset > lastOffset;
  }

}
