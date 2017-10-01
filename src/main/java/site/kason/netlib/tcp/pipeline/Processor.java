package site.kason.netlib.tcp.pipeline;

import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public interface Processor {
  
  int getMinInBufferSize();
  
  int getMinOutBufferSize();
  
  void process(IOBuffer in,IOBuffer out);

}
