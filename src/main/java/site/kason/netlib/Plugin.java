package site.kason.netlib;

import site.kason.netlib.io.IOHandler;

/**
 * @author KasonYang
 */
public interface Plugin {

    IOHandler createIOHandler(IOHandler superHandler, String channel);

}
