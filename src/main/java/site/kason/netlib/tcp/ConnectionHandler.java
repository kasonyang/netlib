package site.kason.netlib.tcp;
/**
 *
 * @author Kason Yang
 */
public interface ConnectionHandler {
    
    public void channelConnected(Channel ch);
    
    //public void connectionClosed(Channel ch);
    
    public void channelConnectFailed(Channel ch,Exception ex);
    
    public void channelClosed(Channel ch);

}
