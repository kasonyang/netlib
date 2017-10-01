package site.kason.netlib.tcp.pipeline;

import site.kason.netlib.tcp.Channel;

/**
 *
 * @author Kason Yang
 */
public interface Codec {
  
  boolean hasEncoder();
  
  Processor getEncoder();
  
  boolean hasDecoder();
  
  Processor getDecoder();

}
