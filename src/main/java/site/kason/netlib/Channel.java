package site.kason.netlib;

import kalang.io.AsyncReader;
import kalang.io.AsyncWriter;
import kalang.lang.Completable;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import site.kason.netlib.io.IOHandler;
import site.kason.netlib.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class Channel implements Hostable, IOHandler {

    private static int ID_COUNTER = 0;

    private final static Logger LOG = Logger.getLogger(Channel.class);

    private final Host host;

    private final int id;

    private final SocketChannel socketChannel;

    private IOTask readTask;

    private IOTask writeTask;

    private Completable.Resolver<Void> connectResolver;

    private boolean closed = false;

    private AsyncReader myReader = new AsyncReader() {
        @Override
        protected Completable<Integer> handleRead(byte[] buffer, int offset, int length) {
            return new Completable<>(resolver -> {
                readTask = new IOTask(buffer, offset, length, resolver);
                requestRead();
            });
        }

        @Override
        public Completable<Void> close() {
            return Channel.this.close();
        }
    };

    private AsyncReader reader = new AsyncReader() {
        @Override
        protected Completable<Integer> handleRead(byte[] buffer, int offset, int length) {
            ensureOpen();
            return ioHandler.getReader().read(buffer, offset, length);
        }

        @Override
        public Completable<Void> close() {
            return ioHandler.getReader().close();
        }
    };


    private AsyncWriter myWriter = new AsyncWriter() {
        @Override
        protected Completable<Integer> handleWrite(byte[] buffer, int offset, int length) {
            return new Completable<>(resolver -> {
                writeTask = new IOTask(buffer, offset, length, resolver);
                requestWrite();
            });
        }

        @Override
        public Completable<Void> close() {
            return Channel.this.close();
        }
    };

    private AsyncWriter writer = new AsyncWriter() {
        @Override
        protected Completable<Integer> handleWrite(byte[] buffer, int offset, int length) {
            ensureOpen();
            return ioHandler.getWriter().write(buffer, offset, length);
        }

        @Override
        public Completable<Void> close() {
            return ioHandler.getWriter().close();
        }
    };


    private IOHandler ioHandler = new IOHandler() {

        @Override
        public AsyncReader getReader() {
            return Channel.this.myReader;
        }

        @Override
        public AsyncWriter getWriter() {
            return Channel.this.myWriter;
        }

    };

    protected Channel(SocketChannel socketChannel, Host host) {
        this.socketChannel = socketChannel;
        this.host = host;
        this.id = ID_COUNTER++;
    }

    public void installPlugin(Plugin... plugins) {
        for (Plugin p : plugins) {
            ioHandler = p.createIOHandler(ioHandler, toString());
        }
    }

    /**
     * connect the channel to the remote a <b>connected</b> event will be trigger
     * if connect successfully or a <b>connectFailed</b> event will be trigger
     *
     * @param remote the remote address to connect
     * @return rejected Completable if failed
     */
    @SneakyThrows
    public Completable<Void> connect(SocketAddress remote) {
        LOG.debug("%s: connecting %s", this, remote);
        return new Completable<>(resolver -> {
            connectResolver = resolver;
            host.updateInterestKeys(this, SelectionKey.OP_CONNECT, true);
            this.socketChannel.connect(remote);
        });
    }

    public Completable<Void> connect(String host, int port) {
        return connect(new InetSocketAddress(host, port));
    }

    public SocketChannel socketChannel() {
        return socketChannel;
    }

    @SneakyThrows
    public Completable<Void> close() {
        try {
            if (this.closed) {
                return Completable.reject(null);
            }
            if (socketChannel == null) {
                return Completable.reject(null);
            }
            this.closed = true;
            host.closeChannel(this);
            socketChannel.close();
            return Completable.reject(null);
        } catch (Throwable ex) {
            return Completable.reject(ex);
        }
    }

    @Override
    public SelectableChannel getSelectableChannel() {
        return this.socketChannel;
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
    protected synchronized void handleWrite() {
        IOTask wt = writeTask;
        pauseWrite();
        LOG.debug("%s: handle write", this);
        //TODO why null
        if (wt == null) {
            return;
        }
        int wlen = 0;
        Throwable error = null;
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(wt.buffer, wt.offset, wt.length);
            wlen = this.socketChannel.write(byteBuffer);
            LOG.debug("%s: write %d bytes", this, wlen);
        } catch (Throwable ex) {
            error = ex;
        } finally {
            writeTask = null;
        }
        if (error != null) {
            wt.resolver.reject(error);
        } else {
            wt.resolver.resolve(wlen);
        }
    }

    @SneakyThrows
    protected synchronized void handleRead() {
        pauseRead();
        LOG.debug("%s: handle read", this);
        IOTask task = this.readTask;
        if (task == null) {
            //TODO why null
            return;
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(task.buffer, task.offset, task.length);
        int rlen = 0;
        Throwable error = null;
        try {
            rlen = this.socketChannel.read(byteBuffer);
            LOG.debug("%s: receive %d bytes", this, rlen);
        } catch (Throwable ex) {
            error = ex;
        } finally {
            readTask = null;
        }
        if (error != null) {
            task.resolver.reject(error);
        } else {
            task.resolver.resolve(rlen);
        }
    }

    @Override
    public String toString() {
        return "Channel#" + id;
    }

    protected void handleConnected() {
        LOG.debug("channel connected: %s %s", this, socketChannel);
        connectResolver.resolve(null);
    }

    protected void handleConnectFailed(IOException ex) {
        connectResolver.reject(ex);
    }

    @SneakyThrows
    private void ensureOpen() {
        if (this.closed) {
            throw new ClosedChannelException();
        }
    }

    private synchronized void requestRead() {
        host.updateInterestKeys(this, SelectionKey.OP_READ, true);
    }

    private synchronized void pauseRead() {
        host.updateInterestKeys(this, SelectionKey.OP_READ, false);
    }

    private synchronized void requestWrite() {
        host.updateInterestKeys(this, SelectionKey.OP_WRITE, true);
    }

    private synchronized void pauseWrite() {
        host.updateInterestKeys(this, SelectionKey.OP_WRITE, false);
    }

    @AllArgsConstructor
    private static class IOTask {
        byte[] buffer;
        int offset;
        int length;
        Completable.Resolver<Integer> resolver;
    }

}
