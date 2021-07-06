package site.kason.netlib.tcp;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public interface Host {

    void continueWrite(Channel ch);

    void pauseWrite(Channel ch);
    
    void continueRead(Channel ch);

    void pauseRead(Channel ch);
    
    void prepareConnect(Channel ch);

    Channel createChannel();
    
    Channel createChannel(SocketChannel sc);
    
    ServerChannel createServerChannel(SocketAddress endpoint, AcceptHandler acceptHandler);
    
    void closeChannel(Hostable ch);
    

}
