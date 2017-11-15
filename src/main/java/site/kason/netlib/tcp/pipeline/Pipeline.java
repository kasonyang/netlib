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
      inBuffer.compact();
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
      }
    }
  }
  
  public int getProcessorCount(){
    return this.processors.size();
  }
  
  public void addProcessor(Processor... ps){
    this.addProcessor(this.processors.size(),ps);
  }
  
  public void addProcessor(int index,Processor... ps) {
    int psLen = ps.length;
    if (psLen <= 0) {
      return;
    }
    int pSize = processors.size();
    if (pSize > 0) {
      int inSizeRequired = ps[0].getMinInBufferSize();
      int preOutIndex = index-1;
      IOBuffer preOutBuffer = preOutIndex>=0 ? this.processorOutBuffers.get(preOutIndex) : null;
      if(preOutBuffer==null || preOutBuffer.array().length<inSizeRequired){
        if(preOutBuffer==null){
          preOutBuffer = this.inBuffer = this.createBuffer(inSizeRequired);
        }else{
          preOutBuffer = this.createBuffer(inSizeRequired);
          this.processorOutBuffers.set(preOutIndex, preOutBuffer);
        }
      } 
      this.processorInBuffers.add(index,preOutBuffer);
      this.processors.add(index,ps[0]);
      for (int i = 1; i < ps.length; i++) {
        int inSize = Math.min(ps[i].getMinInBufferSize(), ps[i - 1].getMinOutBufferSize());
        IOBuffer ib = this.createBuffer(inSize);
        this.processorInBuffers.add(index+i,ib);
        this.processorOutBuffers.add(index+i-1,ib);
        this.processors.add(index+i,ps[i]);
      }
      int minOutBufferSize = ps[psLen - 1].getMinOutBufferSize();
      int outIndex = index+ps.length-1;
      boolean isLast = processors.size()-1==outIndex;
      if(isLast){
        this.outBuffer = this.createBuffer(minOutBufferSize);
        this.processorOutBuffers.add(outBuffer);
      }else{
        IOBuffer nextIn = this.processorInBuffers.get(outIndex+1);
        if(nextIn.array().length<minOutBufferSize){
          this.processorInBuffers.remove(outIndex+1);
          nextIn = this.createBuffer(minOutBufferSize);
          this.processorInBuffers.add(outIndex+1,nextIn);
        }
        this.processorOutBuffers.add(outIndex,nextIn);
      }
    } else {
      int inSizeRequired = ps[0].getMinInBufferSize();
      if (inSizeRequired > this.inBuffer.array().length) {
        inBuffer = this.createBuffer(inSizeRequired);
      }
      this.processorInBuffers.add(inBuffer);
      this.processors.add(ps[0]);
      for (int i = 1; i < ps.length; i++) {
        int is = Math.min(ps[i].getMinInBufferSize(), ps[i - 1].getMinOutBufferSize());
        IOBuffer ib = this.createBuffer(is);
        this.processorInBuffers.add(ib);
        this.processorOutBuffers.add(ib);
        this.processors.add(ps[i]);
      }
      outBuffer =this.createBuffer(ps[psLen - 1].getMinOutBufferSize());
      this.processorOutBuffers.add(outBuffer);
    }
  }
  
  private IOBuffer createBuffer(int minSize){
    if(minSize>4096){
      return IOBuffer.create(minSize);
    }else{
      return IOBuffer.create(4096);
    }
  }

}
