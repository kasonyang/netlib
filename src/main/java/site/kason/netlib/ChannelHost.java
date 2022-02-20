package site.kason.netlib;

import kalang.lang.Completable;
import lombok.SneakyThrows;
import site.kason.netlib.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ChannelHost implements Host {

    private static Logger LOG = Logger.getLogger(ChannelHost.class);

    Selector selector;

    private boolean cancelled = false;

    private HashMap<SelectableChannel, Hostable> channels = new HashMap<>();

    private HashMap<Hostable, SelectableChannel> socketChannels = new HashMap<>();

    private Set<Channel> readQueue = new LinkedHashSet<>();

    private Set<Channel> writeQueue = new LinkedHashSet<>();

    private final Queue<Runnable> tasks = new ConcurrentLinkedDeque<>();

    public static ChannelHost create() throws IOException {
        return new ChannelHost();
    }

    protected ChannelHost() throws IOException {
        selector = Selector.open();
    }

    private boolean isInterest(Channel ch, int key) {
        SocketChannel sc = ch.socketChannel();
        SelectionKey selectionKey = sc.keyFor(selector);
        try {
            int ops = selectionKey.interestOps();
            return (ops & key) != 0;
        } catch (CancelledKeyException ex) {
            //ignore it
            return false;
        }
    }

    private void updateInterestKeys(SelectableChannel channel, int keys, boolean enable) {
        SelectionKey selectionKey = channel.keyFor(selector);
        if (selectionKey == null) {
            return;
        }
        try {
            int ops = selectionKey.interestOps();
            if (enable) {
                ops |= keys;
            } else {
                ops &= ~keys;
            }
            selectionKey.interestOps(ops);
            selector.wakeup();
        } catch (CancelledKeyException ex) {
            //ignore it
        }
    }


    @Override
    public void updateInterestKeys(Channel ch, int keys, boolean enable) {
        LOG.debug("%s: update interest keys: %d=%s", ch, keys, enable ? "on" : "off");
        SocketChannel sc = ch.socketChannel();
        updateInterestKeys(sc, keys, enable);
    }

    @SneakyThrows
    @Override
    public void updateInterestKeys(ServerChannel ch, int keys, boolean enable) {
        LOG.debug("%s: update interest keys: %d=%s", ch, keys, enable ? "on" : "off");
        ServerSocketChannel sc = ch.serverSocketChannel();
        updateInterestKeys(sc, keys, enable);
    }

    public void stopListen() {
        selector.wakeup();
        cancelled = true;
    }

    private void onSocketChannelKey(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        Channel ch = (Channel) channels.get(sc);
        if (ch == null) {
            return;
        }
        if (key.isReadable()) {
            readQueue.add(ch);
        }
        if (key.isWritable()) {
            writeQueue.add(ch);
        }
        if (key.isConnectable()) {
            this.updateInterestKeys(ch, SelectionKey.OP_CONNECT, false);
            try {
                if (sc.isConnectionPending()) {
                    sc.finishConnect();
                    execChannelBusiness(ch, ch::handleConnected);
                }
            } catch (IOException ex) {
                execChannelBusiness(ch, () -> ch.handleConnectFailed(ex));
            }
        }
    }

    @SneakyThrows
    public void listen() {
        for (; ; ) {
            selector.select();
            if (cancelled) {
                return;
            }
            while (!tasks.isEmpty()) {
                tasks.poll().run();
            }
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectionKeys.iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();
                try {
                    processKey(key);
                } catch (Exception ex) {
                    LOG.error(ex);
                }
            }
            List<Channel> readList = new ArrayList<>(this.readQueue);
            this.readQueue.clear();
            for (Channel r : readList) {
                execChannelBusiness(r, r::handleRead);
            }
            List<Channel> writeList = new ArrayList<>(this.writeQueue);
            this.writeQueue.clear();
            for (Channel w : writeList) {
                execChannelBusiness(w, w::handleWrite);
            }
            if (!this.readQueue.isEmpty()) {
                selector.wakeup();
            }
        }
    }

    @SneakyThrows
    private void processKey(SelectionKey key) {
        if (!key.isValid()) {
            return;
        }
        if (key.isAcceptable()) {
            ServerSocketChannel nssc = (ServerSocketChannel) key.channel();
            SocketChannel sc = nssc.accept();
            if (sc == null) {
                return;
            }
            sc.configureBlocking(false);
            Channel ch = createChannel(sc);
            ServerChannel serverChannel = (ServerChannel) channels.get(nssc);
            execChannelBusiness(ch, () -> serverChannel.handleAccept(ch));
        } else {
            try {
                this.onSocketChannelKey(key);
            } catch (CancelledKeyException ex) {
                LOG.debug("%s: key cancelled", key.channel());
            }
        }
    }

    private void execChannelBusiness(Channel channel, Runnable businessCallback) {
        try {
            businessCallback.run();
        } catch (Throwable ex) {
            LOG.error(ex);
        }
    }

    private void hostChannel(Hostable channel) throws ClosedChannelException {
        SelectableChannel sc = channel.getSelectableChannel();
        channels.put(sc, channel);
        socketChannels.put(channel, sc);
        if (sc instanceof ServerSocketChannel) {
            sc.register(selector, 0);
        } else if (sc instanceof SocketChannel) {
            if (!((SocketChannel) sc).isConnected()) {
                sc.register(selector, SelectionKey.OP_CONNECT);
            } else {
                sc.register(selector, 0);
            }
        }
    }

    @Override
    public void closeChannel(Hostable channel) {
        SelectableChannel sc = channel.getSelectableChannel();
        channels.remove(sc);
        socketChannels.remove(channel);
        sc.keyFor(selector).cancel();
        LOG.debug("%s closed", channel);
    }

    @Override
    @SneakyThrows
    public Completable<Channel> createChannel() {
        return new Completable<>(resolver -> {
            submitTask(() -> {
                try {
                    SocketChannel sc = SocketChannel.open();
                    sc.configureBlocking(false);
                    resolver.resolve(createChannel(sc));
                } catch (Exception ex) {
                    resolver.reject(ex);
                }
            });
        });
    }

    @SneakyThrows
    private Channel createChannel(SocketChannel sc) {
        Channel ch = new Channel(sc, this);
        this.hostChannel(ch);
        return ch;
    }

    @Override
    @SneakyThrows
    public Completable<ServerChannel> createServerChannel(SocketAddress endpoint) {
        ChannelHost self = this;
        return new Completable<>(resolver -> {
            submitTask(new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    ServerChannel sc = ServerChannel.create(self);
                    sc.bind(endpoint);
                    hostChannel(sc);
                    resolver.resolve(sc);
                }
            });
        });
    }

    @Override
    public Completable<ServerChannel> createServerChannel(String host, int port) {
        return createServerChannel(new InetSocketAddress(host, port));
    }

    public Completable<ServerChannel> createServerChannel(int port) {
        return createServerChannel(new InetSocketAddress(port));
    }

    private void submitTask(Runnable task) {
        tasks.add(task);
        selector.wakeup();
    }

}
