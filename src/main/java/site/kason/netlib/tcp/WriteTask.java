package site.kason.netlib.tcp;

import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public interface WriteTask {

    /**
     * 
     * @param channel the channel
     * @param buffer the write buffer
     * @return true if task is finished.
     * @throws Exception if some error occurs
     */
    boolean handleWrite(Channel channel,IOBuffer buffer) throws Exception;

}
