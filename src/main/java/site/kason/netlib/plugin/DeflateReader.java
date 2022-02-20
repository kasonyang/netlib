package site.kason.netlib.plugin;

import kalang.io.AsyncReader;
import kalang.lang.Completable;
import lombok.SneakyThrows;

import java.util.zip.Inflater;

/**
 * @author KasonYang
 */
public class DeflateReader extends AsyncReader {

    private final AsyncReader reader;

    private final Inflater inflater;

    private final byte[] decodeBuffer = new byte[4096];

    private int decodeBufferLen = 0;

    public DeflateReader(AsyncReader reader) {
        this.reader = reader;
        this.inflater = new Inflater();
    }

    public DeflateReader(AsyncReader reader, boolean nowrap) {
        this.reader = reader;
        this.inflater = new Inflater(nowrap);
    }

    @Override
    protected Completable<Integer> handleRead(byte[] buffer, int offset, int length) {
        return decodeAndRead(buffer, offset, length);
    }

    @Override
    public Completable<Void> close() {
        return reader.close();
    }

    @SneakyThrows
    private Completable<Integer> decodeAndRead(byte[] buffer, int offset, int length) {
        int n = inflater.inflate(buffer, offset, length);
        if (n > 0) {
            return Completable.resolve(n);
        }
        assert inflater.needsInput() || inflater.needsDictionary();
        int remaining = inflater.getRemaining();
        if (remaining > 0) {
            System.arraycopy(decodeBuffer, decodeBufferLen - remaining, decodeBuffer, 0, remaining);
        }
        return reader.read(decodeBuffer, remaining, decodeBuffer.length - remaining).onCompleted(v -> {
            if (v == -1) {
                return Completable.resolve(-1);
            }
            decodeBufferLen = remaining + v;
            inflater.setInput(decodeBuffer, 0, decodeBufferLen);
            return decodeAndRead(buffer, offset, length);
        });
    }

}
