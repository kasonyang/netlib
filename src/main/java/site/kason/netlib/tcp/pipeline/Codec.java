package site.kason.netlib.tcp.pipeline;

import javax.annotation.Nullable;

/**
 * @author Kason Yang
 */
public interface Codec {

  @Nullable
  Processor getEncoder();

  @Nullable
  Processor getDecoder();

}
