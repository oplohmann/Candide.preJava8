package org.objectscape.candide.common;

/**
 * Created with IntelliJ IDEA.
 * User: Oliver
 * Date: 27.05.13
 * Time: 18:17
 * To change this template use File | Settings | File Templates.
 */
public class ValueHolderEvent<V> {

    protected final String name;
    protected final V value;
    protected final int invocationCount;

    public ValueHolderEvent(ValueHolderEvent<V> event) {
        this.name = event.getName();
        this.value = event.getValue();
        this.invocationCount = event.getInvocationCount();
    }

    public ValueHolderEvent(String name, V value, int invocationCount) {
        this.name = name;
        this.value = value;
        this.invocationCount = invocationCount;
    }

    public ValueHolderEvent(ValueHolderEvent<V> event, int invocationCount) {
        this.name = event.getName();
        this.value = event.getValue();
        this.invocationCount = invocationCount;
    }

    public String getName() {
        return name;
    }

    public V getValue() {
        return value;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

}
