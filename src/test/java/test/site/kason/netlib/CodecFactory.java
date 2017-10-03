package test.site.kason.netlib;

import java.util.List;
import site.kason.netlib.tcp.Channel;
import site.kason.netlib.tcp.pipeline.Codec;

/**
 *
 * @author Kason Yang
 */
public interface CodecFactory {
  
  List<Codec> createCodecs(Channel ch);
  
}
