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
     * @throws Exception if some error occurs
     */
    boolean handleRead(Channel channel,IOBuffer buffer) throws Exception;

}
