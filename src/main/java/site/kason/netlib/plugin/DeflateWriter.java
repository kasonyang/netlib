package site.kason.netlib.plugin;

import kalang.io.AsyncWriter;
import kalang.lang.Completable;

import java.util.zip.Deflater;

/**
 * @author KasonYang
 */
public class DeflateWriter extends AsyncWriter {

    private final AsyncWriter writer;

    private final byte[] encodeBuffer = new byte[4096];

    private final Deflater deflater;

    public DeflateWriter(AsyncWriter writer) {
        this.writer = writer;
        this.deflater = new Deflater();
    }

    public DeflateWriter(AsyncWriter writer, int level) {
        this.writer = writer;
        this.deflater = new Deflater(level);
    }

    public DeflateWriter(AsyncWriter writer, int level, boolean nowrap) {
        this.writer = writer;
        this.deflater = new Deflater(level, nowrap);
    }

    @Override
    protected Completable<Integer> handleWrite(byte[] buffer, int offset, int length) {
        deflater.setInput(buffer, offset, length);
        int oldTotalIn = deflater.getTotalIn();
        return endcodeAndWrite().onCompleted(v -> {
            int consumeBytes = deflater.getTotalIn() - oldTotalIn;
            return Completable.resolve(consumeBytes);
        });
    }

    @Override
    public Completable<Void> close() {
        return writer.close();
    }

    private Completable<Void> endcodeAndWrite() {
        int produceBytes = deflater.deflate(encodeBuffer, 0, encodeBuffer.length, Deflater.SYNC_FLUSH);
        if (produceBytes <= 0) {
            return Completable.resolve(null);
        }
        return writer.writeFully(encodeBuffer, 0, produceBytes).onCompleted(v -> {
            return endcodeAndWrite();
        });
    }

}
