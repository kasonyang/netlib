package site.kason.netlib.tcp;

import site.kason.netlib.io.IOBuffer;

/**
 * @author Kason Yang
 */
public interface WriteTask {

  /**
   * @param channel the channel
   * @param buffer  the write buffer
   * @return true if task is finished.
   * @throws Exception if some error occurs
   */
  boolean handleWrite(Channel channel, IOBuffer buffer) throws Exception;

  /**
   * Call when handleWrite returning true and buffer flushed.
   * @param channel the channel written
   */
  default void handleWritten(Channel channel) {

  }

}
