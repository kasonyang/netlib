package site.kason.netlib.tcp;

import java.nio.channels.SelectableChannel;

public interface Hostable {

    SelectableChannel getSelectableChannel();

}
