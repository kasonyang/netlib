package site.kason.netlib.tcp;
/**
 *
 * @author Kason Yang
 */
public interface ConnectionListener {
    
    void onChannelConnected(Channel ch);
    
    //public void connectionClosed(Channel ch);
    
    void onChannelConnectFailed(Channel ch, Exception ex);
    
    void onChannelClosed(Channel ch);

    void onReadCompleted(Channel ch);

}
