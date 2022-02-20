package site.kason.netlib;

import kalang.lang.Completable;

import java.net.SocketAddress;

public interface Host {

    void updateInterestKeys(ServerChannel ch, int keys, boolean enable);

    void updateInterestKeys(Channel ch, int keys, boolean enable);

    Completable<Channel> createChannel();

    Completable<ServerChannel> createServerChannel(SocketAddress endpoint);

    Completable<ServerChannel> createServerChannel(String host, int port);

    Completable<ServerChannel> createServerChannel(int port);

    void closeChannel(Hostable ch);


}
