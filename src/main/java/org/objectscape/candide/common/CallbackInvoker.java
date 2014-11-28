package org.objectscape.candide.common;

import org.objectscape.candide.concurrent.ListenerValue;
import org.objectscape.candide.util.function.Consumer;

/**
 * Created with IntelliJ IDEA.
 * User: plohmann
 * Date: 24.10.13
 * Time: 13:20
 * To change this template use File | Settings | File Templates.
 */
public class CallbackInvoker  {

    public <EventType, ListenerType extends Consumer<EventType>>
    void invoke(final ListenerType listener, ListenerValue listenerValue, final EventType event)
    {
        listener.accept(event);
    }

}
