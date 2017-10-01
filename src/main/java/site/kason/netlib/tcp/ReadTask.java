package site.kason.netlib.tcp;

import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public interface ReadTask {

    /**
     * 
     * @param transfer
     * @return true if task is finished
     */
    boolean handleRead(IOBuffer buffer) throws Exception;

}
