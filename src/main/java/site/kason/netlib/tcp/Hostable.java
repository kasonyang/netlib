package site.kason.netlib.tcp;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;

public interface Hostable {

    public SelectableChannel getSelectableChannel();

}
