package site.kason.netlib.codec;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.pipeline.Processor;

/**
 *
 * @author Kason Yang
 */
public class DeflateDecodeProcessor implements Processor {
  

  public DeflateDecodeProcessor() {
    
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
    Inflater inflater = new Inflater();
    inflater.setInput(in.array(),in.getReadPosition(),in.getReadableSize());
    int oldTotalIn = inflater.getTotalIn();
    try {
      int result = inflater.inflate(out.array(), out.getWritePosition(), out.getWritableSize());
      out.setWritePosition(out.getWritePosition()+result);
      int consumed = inflater.getTotalIn() - oldTotalIn;
      in.moveReadPosition(consumed);
    } catch (DataFormatException ex) {
      //TODO handle ex
      throw new RuntimeException(ex);
    }
  }

}
