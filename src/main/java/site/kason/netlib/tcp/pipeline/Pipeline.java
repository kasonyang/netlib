package site.kason.netlib.tcp.pipeline;

import java.util.LinkedList;
import java.util.List;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.tcp.Channel;

/**
 *
 * @author Kason Yang
 */
public class Pipeline {

  private final IOBuffer inBuffer;

  private final IOBuffer outBuffer;

  private final Processor[] processors;

  private final IOBuffer[] processorInBuffers;

  private final IOBuffer[] processorOutBuffers;

  public Pipeline(Processor... processors) {
    //TODO compact
    int pSize = processors.length;
    this.processors = new Processor[pSize];
    this.processorInBuffers = new IOBuffer[pSize];
    this.processorOutBuffers = new IOBuffer[pSize];
    if (pSize > 0) {
      System.arraycopy(processors, 0, this.processors, 0, pSize);
      inBuffer = processorInBuffers[0] = IOBuffer.create(processors[0].getMinInBufferSize());
      for (int i = 1; i < pSize; i++) {
        int inSize = Math.max(processors[i - 1].getMinOutBufferSize(), processors[i].getMinInBufferSize());
        IOBuffer buffer = IOBuffer.create(inSize);
        processorInBuffers[i] = processorOutBuffers[i - 1] = buffer;
      }
      outBuffer = processorOutBuffers[pSize - 1] = IOBuffer.create(processors[pSize - 1].getMinOutBufferSize());
    } else {
      //TODO fix size
      inBuffer = outBuffer = IOBuffer.create(4096);
    }

  }

  public IOBuffer getInBuffer() {
    return inBuffer;
  }

  public IOBuffer getOutBuffer() {
    return outBuffer;
  }

  public void process() {
    //TODO loop
    this.outBuffer.compact();
    for (int i = 0; i < processors.length; i++) {
      processorOutBuffers[i].compact();
      processors[i].process(processorInBuffers[i], processorOutBuffers[i]);
      processorInBuffers[i].compact();
    }
    this.inBuffer.compact();
  }

}
