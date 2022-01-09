package site.kason.netlib.tcp;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public interface Host {

    void registerWrite(Channel ch);

    void unregisterWrite(Channel ch);

    boolean isWriteRegistered(Channel ch);
    
    void registerRead(Channel ch);

    void unregisterRead(Channel ch);

    boolean isReadRegistered(Channel ch);
    
    void requestConnect(Channel ch);

    Channel createChannel();
    
    Channel createChannel(SocketChannel sc);
    
    ServerChannel createServerChannel(SocketAddress endpoint, AcceptHandler acceptHandler);
    
    void closeChannel(Hostable ch);
    

}
