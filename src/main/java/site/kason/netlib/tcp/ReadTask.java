package site.kason.netlib.tcp;

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
    boolean handleRead(Transfer transfer) throws Exception;

}
