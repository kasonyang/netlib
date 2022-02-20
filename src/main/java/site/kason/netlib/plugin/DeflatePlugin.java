package site.kason.netlib.plugin;

import kalang.io.AsyncReader;
import kalang.io.AsyncWriter;
import kalang.lang.Completable;
import lombok.SneakyThrows;
import site.kason.netlib.Plugin;
import site.kason.netlib.io.IOHandler;

import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author KasonYang
 */
public class DeflatePlugin implements Plugin {

    @Override
    public IOHandler createIOHandler(IOHandler superHandler, String channel) {
        return new DeflateIOHandler(superHandler);
    }

    private static class DeflateIOHandler implements IOHandler {

        private final AsyncReader reader;

        private final AsyncWriter writer;

        public DeflateIOHandler(IOHandler superHandler) {
            this.reader = new DeflateReader(superHandler.getReader());
            this.writer = new DeflateWriter(superHandler.getWriter());
        }

        @Override
        public AsyncReader getReader() {
            return reader;
        }

        @Override
        public AsyncWriter getWriter() {
            return writer;
        }





    }

}
