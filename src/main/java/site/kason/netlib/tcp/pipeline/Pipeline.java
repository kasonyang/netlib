package site.kason.netlib.tcp.pipeline;

import java.util.ArrayList;
import java.util.List;
import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public class Pipeline {

  private IOBuffer inBuffer;

  private IOBuffer outBuffer;

  private final List<Processor> processors = new ArrayList();

  private final List<IOBuffer> processorInBuffers = new ArrayList();

  private final List<IOBuffer> processorOutBuffers = new ArrayList();

  public Pipeline() {
    inBuffer = outBuffer = IOBuffer.create(4096);
  }

  public IOBuffer getInBuffer() {
    return inBuffer;
  }

  public IOBuffer getOutBuffer() {
    return outBuffer;
  }

  public void process() {
    boolean produced = true;
    while(produced){
      produced = false;
      for (int i = 0; i < processors.size(); i++) {
        IOBuffer in = processorInBuffers.get(i);
        IOBuffer out = processorOutBuffers.get(i);
        out.compact();
        int oldOutWritePos = out.getWritePosition();
        processors.get(i).process(in, out);
        int written = out.getWritePosition()-oldOutWritePos;
        if(written>0){
          produced = true;
        }
        in.compact();
      }
    }
  }

  public void addProcessor(Processor... ps) {
    int psLen = ps.length;
    if (psLen <= 0) {
      return;
    }
    int pSize = processors.size();
    if (pSize > 0) {
      int inSizeRequired = ps[0].getMinInBufferSize();
      IOBuffer lastOutBuffer = this.processorOutBuffers.get(pSize - 1);
      IOBuffer pInBuffer;
      if (inSizeRequired > lastOutBuffer.array().length) {
        pInBuffer = IOBuffer.create(pSize);
        this.processorOutBuffers.set(pSize - 1, pInBuffer);
      } else {
        pInBuffer = lastOutBuffer;
      }
      this.processorInBuffers.add(pInBuffer);
      this.processors.add(ps[0]);
      for (int i = 1; i < ps.length; i++) {
        int inSize = Math.min(ps[i].getMinInBufferSize(), ps[i - 1].getMinOutBufferSize());
        IOBuffer ib = IOBuffer.create(inSize);
        this.processorInBuffers.add(ib);
        this.processorOutBuffers.add(ib);
        this.processors.add(ps[i]);
      }
      outBuffer = IOBuffer.create(ps[psLen - 1].getMinOutBufferSize());
      this.processorOutBuffers.add(outBuffer);
    } else {
      int inSizeRequired = ps[0].getMinInBufferSize();
      if (inSizeRequired > this.inBuffer.array().length) {
        inBuffer = IOBuffer.create(inSizeRequired);
      }
      this.processorInBuffers.add(inBuffer);
      this.processors.add(ps[0]);
      for (int i = 1; i < ps.length; i++) {
        int is = Math.min(ps[i].getMinInBufferSize(), ps[i - 1].getMinOutBufferSize());
        IOBuffer ib = IOBuffer.create(is);
        this.processorInBuffers.add(ib);
        this.processorOutBuffers.add(ib);
        this.processors.add(ps[i]);
      }
      outBuffer = IOBuffer.create(ps[psLen - 1].getMinOutBufferSize());
      this.processorOutBuffers.add(outBuffer);
    }
  }

}
