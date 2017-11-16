package site.kason.netlib.codec;

import java.util.zip.Deflater;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.pipeline.Processor;

/**
 *
 * @author Kason Yang
 */
public class DeflateEncodeProcessor implements Processor {

  public DeflateEncodeProcessor() {
  }

  @Override
  public int getMinInBufferSize() {
    return 4096;
  }

  @Override
  public int getMinOutBufferSize() {
    return 4096;
  }

  @Override
  public void process(IOBuffer in, IOBuffer out) {
    if(in.getReadableSize()<=0) return;
    if(out.getWritableSize()<=0) return;
    Deflater deflater = new Deflater();
    deflater.setInput(in.array(),in.getReadPosition(),in.getReadableSize());
    deflater.finish();
    int oldTotalIn = deflater.getTotalIn();
    int result = deflater.deflate(out.array(),out.getWritePosition(),out.getWritableSize());
    out.setWritePosition(out.getWritePosition()+result);
    int consumed = deflater.getTotalIn() - oldTotalIn;
    in.moveReadPosition(consumed);
  }

}
