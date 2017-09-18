package site.kason.netlib.tcp;

/**
 *
 * @author Kason Yang
 */
public interface ChannelFilter {

    ReadTask filterRead(ReadTask task);

    WriteTask filterWrite(WriteTask task);
    
    void install(Channel ch);

}
