package site.kason.netlib.codec;

import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.pipeline.Codec;
import site.kason.netlib.tcp.pipeline.CodecInitProgress;
import site.kason.netlib.tcp.pipeline.Processor;

/**
 * @author Kason Yang
 */
public class DeflateCodec implements Codec {

  DeflateEncodeProcessor encoder = new DeflateEncodeProcessor();

  DeflateDecodeProcessor decoder = new DeflateDecodeProcessor();

  @Override
  public Processor getEncoder() {
    return encoder;
  }

  @Override
  public Processor getDecoder() {
    return decoder;
  }
}
