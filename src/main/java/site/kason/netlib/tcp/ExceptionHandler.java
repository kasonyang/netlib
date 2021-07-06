package site.kason.netlib.tcp;

/**
 *
 * @author Kason Yang
 */
public interface ExceptionHandler {

    void handleException(Channel ch, Throwable ex);

}
