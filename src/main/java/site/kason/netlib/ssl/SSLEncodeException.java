package site.kason.netlib.ssl;

/**
 *
 * @author Kason Yang
 */
public class SSLEncodeException extends SSLException {

  public SSLEncodeException() {
  }

  public SSLEncodeException(String message) {
    super(message);
  }

  public SSLEncodeException(String message, Throwable cause) {
    super(message, cause);
  }

  public SSLEncodeException(Throwable cause) {
    super(cause);
  }

}
