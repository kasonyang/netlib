package site.kason.netlib.tcp.pipeline;

import site.kason.netlib.tcp.Channel;

import javax.annotation.Nullable;

/**
 * @author Kason Yang
 */
public interface Codec {

  void init(Channel channel, CodecInitProgress progress);

  @Nullable
  Processor getEncoder();

  @Nullable
  Processor getDecoder();

}
