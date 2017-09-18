package site.kason.netlib.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public class DefaultTransfer implements Transfer {

  private SocketChannel socket;

  public DefaultTransfer(SocketChannel socket) {
    this.socket = socket;
  }

  @Override
  public int write(IOBuffer buffer) throws IOException {
    int readableSize = buffer.getReadableSize();
    if (readableSize > 0) {
      ByteBuffer bb = ByteBuffer.wrap(buffer.array(), buffer.getReadPosition(), buffer.getReadableSize());
      int written = socket.write(bb);
      buffer.skip(written);
      return written;
    }
    return 0;
  }

  @Override
  public int read(IOBuffer buffer) throws IOException {
    int writePosition = buffer.getWritePosition();
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer.array(), writePosition, buffer.getWritableSize());
    int rlen = socket.read(byteBuffer);
    if (rlen > 0) {
      buffer.setWritePosition(writePosition + rlen);
    }
    return rlen;
  }

}
