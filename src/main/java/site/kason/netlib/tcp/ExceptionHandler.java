package site.kason.netlib.tcp;

/**
 *
 * @author Kason Yang
 */
public interface ExceptionHandler {

    public void handleException(Channel ch, Exception ex);

}
