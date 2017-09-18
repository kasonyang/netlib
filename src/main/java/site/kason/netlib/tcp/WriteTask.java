package site.kason.netlib.tcp;

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
    boolean handleWrite(Transfer transfer) throws Exception;

}
