package site.kason.netlib;

import java.nio.channels.SelectableChannel;

public interface Hostable {

    SelectableChannel getSelectableChannel();

}
