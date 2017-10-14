package site.kason.netlib.codec;

import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.pipeline.Processor;

/**
 *
 * @author Kason Yang
 */
public class DeflateCodec implements Codec {

  @Override
  public boolean hasEncoder() {
    return true;
  }

  @Override
  public Processor getEncoder() {
    return new DeflateEncodeProcessor();
  }

  @Override
  public boolean hasDecoder() {
    return true;
  }

  @Override
  public Processor getDecoder() {
    return new DeflateDecodeProcessor();
  }

}
