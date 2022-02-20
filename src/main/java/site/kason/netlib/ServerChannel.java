package site.kason.netlib;

import kalang.annotation.Nullable;
import kalang.lang.Completable;
import site.kason.netlib.util.StateUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

public class ServerChannel implements Hostable {

    private final ServerSocketChannel ssc;

    private final Host host;

    private Completable.Resolver<Channel> connectResolver;

    public static ServerChannel create(Host host) throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        return new ServerChannel(host, ssc);
    }

    public ServerChannel(Host host, ServerSocketChannel ssc) {
        this.ssc = ssc;
        this.host = host;
    }

    public ServerSocketChannel serverSocketChannel() {
        return ssc;
    }

    public void bind(SocketAddress endpoint) throws IOException {
        ServerSocket socket = ssc.socket();
        socket.bind(endpoint);
    }

    @Override
    public SelectableChannel getSelectableChannel() {
        return this.ssc;
    }

    public Completable<Channel> accept() {
        StateUtil.requireNull(connectResolver, "Previous accept is not completed");
        return new Completable<>(resolver -> {
            connectResolver = resolver;
            host.updateInterestKeys(this, SelectionKey.OP_ACCEPT, true);
        });
    }

    protected void handleAccept(@Nullable Channel ch) {
        host.updateInterestKeys(this, SelectionKey.OP_ACCEPT, false);
        try {
            connectResolver.resolve(ch);
        } finally {
            connectResolver = null;
        }
    }

}
