package site.kason.netlib.plugin;

import kalang.io.AsyncReader;
import kalang.io.AsyncWriter;
import kalang.lang.Completable;
import lombok.SneakyThrows;
import site.kason.netlib.Plugin;
import site.kason.netlib.io.IOBuffer;
import site.kason.netlib.io.IOHandler;
import site.kason.netlib.util.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import java.io.EOFException;
import java.nio.ByteBuffer;

/**
 * @author KasonYang
 */
public class SSLPlugin implements Plugin {

    private final SSLContext context;
    private final boolean clientMode;

    public SSLPlugin(SSLContext context, boolean clientMode) {
        this.context = context;
        this.clientMode = clientMode;
    }

    @Override
    public IOHandler createIOHandler(IOHandler superHandler, String channel) {
        SSLEngine sslEngine = context.createSSLEngine();
        sslEngine.setUseClientMode(clientMode);
        return new SSLIOHandler(superHandler, sslEngine, channel);
    }

    private static class SSLIOHandler implements IOHandler {

        private final static Logger LOG = Logger.getLogger(SSLIOHandler.class);

        private boolean initialized = false;

        private Completable<Void> initializeResult;

        private final IOHandler superHandler;

        private final SSLEngine sslEngine;

        private final IOBuffer wrapOutBuffer;

        private final IOBuffer unwrapInBuffer;

        private final IOBuffer unwrapOutBuffer;

        private String channel;

        private AsyncReader reader = new AsyncReader() {
            @Override
            protected Completable<Integer> handleRead(byte[] buffer, int offset, int length) {
                if (!initialized) {
                    return initialize().onCompleted(v -> {
                        return unwrapAndRead(buffer, offset, length);
                    });
                }
                return unwrapAndRead(buffer, offset, length);
            }

            @Override
            public Completable<Void> close() {
                return superHandler.getReader().close();
            }
        };

        private AsyncWriter writer = new AsyncWriter() {
            @Override
            protected Completable<Integer> handleWrite(byte[] buffer, int offset, int length) {
                if (!initialized) {
                    return initialize().onCompleted(v -> {
                        return wrapAndWrite(buffer, offset, length);
                    });
                } else {
                    return wrapAndWrite(buffer, offset, length);
                }
            }

            @Override
            public Completable<Void> close() {
                return superHandler.getWriter().close();
            }
        };

        private SSLIOHandler(IOHandler superHandler, SSLEngine sslEngine, String channel) {
            this.superHandler = superHandler;
            this.sslEngine = sslEngine;
            SSLSession session = sslEngine.getSession();
            int bufferSize = session.getPacketBufferSize();
            unwrapInBuffer = IOBuffer.create(bufferSize);
            wrapOutBuffer = IOBuffer.create(bufferSize);
            unwrapOutBuffer = IOBuffer.create(bufferSize);
            this.channel = channel;
        }

        @Override
        public AsyncReader getReader() {
            return reader;
        }

        @Override
        public AsyncWriter getWriter() {
            return writer;
        }

        @SneakyThrows
        private Completable<Integer> wrapAndWrite(byte[] in, int offset, int length) {
            ByteBuffer inBuffer = ByteBuffer.wrap(in, offset, length);
            SSLEngineResult res = sslEngine.wrap(inBuffer, wrapOutBuffer.toWriteByteBuffer());
            int byteConsumed = res.bytesConsumed();
            int byteProduced = res.bytesProduced();
            if (byteProduced > 0) {
                wrapOutBuffer.moveWritePosition(byteProduced);
                return superHandler.getWriter().writeFully(wrapOutBuffer.array(), wrapOutBuffer.getReadPosition(), wrapOutBuffer.getReadableSize())
                        .onCompleted(v -> {
                            wrapOutBuffer.clear();
                            return Completable.resolve(byteConsumed);
                        });
            } else {
                return Completable.resolve(byteConsumed);
            }
        }

        private Completable<Integer> unwrapAndRead(byte[] out, int offset, int length) {
            int n = unwrap(out, offset, length);
            if (n > 0) {
                return Completable.resolve(n);
            }
            return superHandler.getReader().read(unwrapInBuffer.array(), unwrapInBuffer.getWritePosition(), unwrapInBuffer.getWritableSize())
                    .onCompleted(v -> {
                        if (v == -1) {
                            return Completable.resolve(-1);
                        }
                        unwrapInBuffer.moveWritePosition(v);
                        return unwrapAndRead(out, offset, length);
                    });
        }

        @SneakyThrows
        private int unwrap(byte[] out, int offset, int length) {
            int unwrapOutRemaining = unwrapOutBuffer.getReadableSize();
            if (unwrapOutRemaining > 0) {
                int copySize = Math.min(length, unwrapOutRemaining);
                System.arraycopy(unwrapOutBuffer.array(), unwrapOutBuffer.getReadPosition(), out, offset, copySize);
                unwrapOutBuffer.moveReadPosition(copySize);
                return copySize;
            }
            unwrapOutBuffer.compact();
            SSLEngineResult res = sslEngine.unwrap(unwrapInBuffer.toReadByteBuffer(), unwrapOutBuffer.toWriteByteBuffer());
            int byteConsumed = res.bytesConsumed();
            int byteProduced = res.bytesProduced();
            if (byteConsumed > 0) {
                unwrapInBuffer.moveReadPosition(byteConsumed);
                unwrapInBuffer.compact();
            }
            if (byteProduced > 0) {
                unwrapOutBuffer.moveWritePosition(byteProduced);
                return unwrap(out, offset, length);
            }
            return 0;
        }

        private Completable<Void> initialize() {
            if (initializeResult == null) {
                initializeResult = new Completable<Void>(resolver -> {
                    handshake(resolver, sslEngine.getHandshakeStatus());
                }).onCompleted(v -> {
                    initialized = true;
                    return Completable.resolve(null);
                });
            }
            return initializeResult;
        }

        @SneakyThrows
        private void handshake(Completable.Resolver<Void> resolver, SSLEngineResult.HandshakeStatus hs) {
            LOG.debug("%s: handshake status: %s", channel, hs);
            if (hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                sslEngine.beginHandshake();
                this.handshake(resolver, sslEngine.getHandshakeStatus());
            } else if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = sslEngine.getDelegatedTask()) != null) {
                    runnable.run();
                }
                handshake(resolver, sslEngine.getHandshakeStatus());
            } else if (hs == SSLEngineResult.HandshakeStatus.FINISHED) {
                resolver.resolve(null);
            } else if (hs == SSLEngineResult.HandshakeStatus.NEED_WRAP || hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                IOBuffer readBuffer = unwrapInBuffer;
                IOBuffer writeBuffer = wrapOutBuffer;
                SSLEngineResult result = hs == SSLEngineResult.HandshakeStatus.NEED_WRAP
                        ? sslEngine.wrap(readBuffer.toReadByteBuffer(), writeBuffer.toWriteByteBuffer())
                        : sslEngine.unwrap(readBuffer.toReadByteBuffer(), writeBuffer.toWriteByteBuffer());
                handleResult(result, readBuffer, writeBuffer).onCompleted(v -> {
                    handshake(resolver, result.getHandshakeStatus());
                    return Completable.resolve(null);
                }).onFailed(error -> {
                    resolver.reject(error);
                    return Completable.resolve(null);
                });
            } else {
                throw new RuntimeException("unknown handshake status:" + hs);
            }
        }

        private Completable<Void> handleResult(SSLEngineResult result, IOBuffer readBuffer, IOBuffer writeBuffer) {
            int byteConsumed = result.bytesConsumed();
            if (byteConsumed > 0) {
                LOG.debug("%s: handshake consumes %d bytes", channel, byteConsumed);
                readBuffer.moveReadPosition(byteConsumed);
                readBuffer.compact();
            }
            return writeProducedBytes(result, writeBuffer).onCompleted(v -> {
                SSLEngineResult.Status status = result.getStatus();
                if (status == SSLEngineResult.Status.OK) {
                    return Completable.resolve(null);
                } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    throw new RuntimeException("unexpected buffer overflow status");
                } else if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    return superHandler.getReader().read(readBuffer.array(), readBuffer.getWritePosition(), readBuffer.getWritableSize()).onCompleted(len -> {
                        if (len == -1) {
                            throw new EOFException();
                        }
                        readBuffer.moveWritePosition(len);
                        return Completable.resolve(null);
                    });
                } else {
                    throw new RuntimeException("unexpected status:" + status);
                }
            });
        }


        private Completable<Void> writeProducedBytes(SSLEngineResult result, IOBuffer writeBuffer) {
            int byteProduced = result.bytesProduced();
            if (byteProduced > 0) {
                LOG.debug("%s: handshake produces %d bytes", channel, byteProduced);
                writeBuffer.setWritePosition(writeBuffer.getWritePosition() + byteProduced);
                return superHandler.getWriter().writeFully(writeBuffer.array(), writeBuffer.getReadPosition(), writeBuffer.getReadableSize()).onCompleted(v -> {
                    writeBuffer.clear();
                    return Completable.resolve(null);
                });
            }
            return Completable.resolve(null);
        }

    }

}
