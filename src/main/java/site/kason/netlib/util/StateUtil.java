package site.kason.netlib.util;

/**
 * @author KasonYang
 */
public class StateUtil {

    public static void requireNull(Object obj, String message) {
        if (obj != null) {
            throw new IllegalStateException(message);
        }
    }

}
