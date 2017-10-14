package site.kason.netlib.ssl;

/**
 *
 * @author Kason Yang
 */
public class SSLException extends RuntimeException {

  public SSLException() {
  }

  public SSLException(String message) {
    super(message);
  }

  public SSLException(String message, Throwable cause) {
    super(message, cause);
  }

  public SSLException(Throwable cause) {
    super(cause);
  }

}
