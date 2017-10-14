package site.kason.netlib.ssl;

/**
 *
 * @author Kason Yang
 */
public class SSLDecodeException extends SSLException {

  public SSLDecodeException() {
  }

  public SSLDecodeException(String message) {
    super(message);
  }

  public SSLDecodeException(String message, Throwable cause) {
    super(message, cause);
  }

  public SSLDecodeException(Throwable cause) {
    super(cause);
  }

}
