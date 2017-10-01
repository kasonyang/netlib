package site.kason.netlib.tcp.pipeline;

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
