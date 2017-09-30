package site.kason.netlib.tcp.pipeline;

import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.Channel;

/**
 *
 * @author Kason Yang
 */
public interface Processor {
  
  int getMinInBufferSize();
  
  int getMinOutBufferSize();
  
  void process(IOBuffer in,IOBuffer out);

}
