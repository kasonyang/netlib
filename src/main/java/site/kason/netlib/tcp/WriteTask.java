package site.kason.netlib.tcp;

import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public interface WriteTask {

    /**
     * 
     * @param transfer
     * @return true if task is finished.
     */
    boolean handleWrite(IOBuffer buffer) throws Exception;

}
