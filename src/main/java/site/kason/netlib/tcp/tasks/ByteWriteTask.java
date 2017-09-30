package site.kason.netlib.tcp.tasks;

import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.Transfer;
import site.kason.netlib.tcp.WriteTask;

/**
 *
 * @author Kason Yang
 */
public class ByteWriteTask implements WriteTask {

  private final IOBuffer buffer;

  public ByteWriteTask(byte[] data) {
    buffer = IOBuffer.create(data.length);
    buffer.push(data);
  }

  @Override
  public boolean handleWrite(IOBuffer buffer) throws Exception {
    //TODO impl
    //transfer.write(buffer);
    //return buffer.getReadableSize() <= 0;
    throw new UnsupportedOperationException();
  }
  
}
