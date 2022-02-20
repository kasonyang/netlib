package site.kason.netlib.io;

import kalang.io.AsyncReader;
import kalang.io.AsyncWriter;

/**
 * @author KasonYang
 */
public interface IOHandler {

    AsyncReader getReader();

    AsyncWriter getWriter();

}
