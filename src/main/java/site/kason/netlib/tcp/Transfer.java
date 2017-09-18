package site.kason.netlib.tcp;

import java.io.IOException;
import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public interface Transfer {

    int write(IOBuffer buffer) throws IOException;

    int read(IOBuffer buffer) throws IOException;

}
