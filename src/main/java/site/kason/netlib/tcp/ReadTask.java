package site.kason.netlib.tcp;

import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public interface ReadTask {

    /**
     * 
     * @param channel the channel
     * @param buffer the read buffer
     * @return true if task is finished
     */
    boolean handleRead(Channel channel,IOBuffer buffer);

}
