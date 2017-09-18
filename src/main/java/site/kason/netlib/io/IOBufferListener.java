package site.kason.netlib.io;
/**
 *
 * @author Kason Yang
 */
public interface IOBufferListener {
    
    void pushed(IOBuffer ioBuffer);
    
    void polled(IOBuffer ioBuffer);

}
