package site.kason.netlib.tcp;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

public interface Host {
    //public void writeBufferChanged(Channel ch,IOBuffer writeBuffer);
    //public void acceptRegister(Channel channel) throws ClosedChannelException;

    //void acceptRegister(Hostable channel) throws ClosedChannelException;
    
    void continueWrite(Channel ch);

    void pauseWrite(Channel ch);
    
    void continueRead(Channel ch);

    void pauseRead(Channel ch);
    
    void prepareConnect(Channel ch);
    
    
    public Channel createChannel() throws IOException;
    
    public Channel createChannel(SocketChannel sc) throws ClosedChannelException;
    
    public ServerChannel createServerChannel(SocketAddress endpoint, AcceptHandler acceptHandler) throws IOException;
    
    public void closeChannel(Hostable ch);
    

}
