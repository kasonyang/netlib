package site.kason.netlib.tcp;
/**
 *
 * @author Kason Yang
 */
public interface ConnectionHandler {
    
    void channelConnected(Channel ch);
    
    //public void connectionClosed(Channel ch);
    
    void channelConnectFailed(Channel ch, Exception ex);
    
    void channelClosed(Channel ch);

}
