/**
 * Copyright (c) 2013 Oliver Plohmann
 * http://www.objectscape.org/candide
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.objectscape.candide.stm;

import org.objectscape.candide.common.CallbackInvoker;
import org.objectscape.candide.common.SendEvent;
import org.objectscape.candide.common.SendListener;
import org.objectscape.candide.concurrent.ListenerValue;
import org.objectscape.candide.concurrent.SetEvent;
import org.objectscape.candide.concurrent.SetListener;
import org.objectscape.candide.util.function.Function;

import java.util.Map;
import java.util.concurrent.Callable;

import static scala.concurrent.stm.japi.STM.*;

public class ListenableAtomicValue<V> {

    protected String name = null;
    protected  V immutableValue = null;

    protected Map<SetListener<V>, ListenerValue> setListeners = newMap();
    protected Map<SendListener<V>, ListenerValue> sendListeners = newMap();

    protected CallbackInvoker invoker = new CallbackInvoker();

    protected ListenableAtomicValue() {
        super();
    }

    public ListenableAtomicValue(V immutableValue) {
        super();
        this.immutableValue = immutableValue;
    }

    public ListenableAtomicValue(String name, V immutableValue) {
        super();
        this.name = name;
        this.immutableValue = immutableValue;
    }

    public V setAndGet(final Function<V, V> function) {
        return atomic(new Callable<V>() {
            public V call() throws Exception {
                V previousValue = immutableValue;
                immutableValue = function.apply(immutableValue);
                notifySetListeners(previousValue);
                return immutableValue;
            }
        });
    }

    public V getAndSet(final Function<V, V> function) {
        return atomic(new Callable<V>() {
            public V call() throws Exception {
                V previousValue = immutableValue;
                immutableValue = function.apply(immutableValue);
                notifySetListeners(previousValue);
                return previousValue;
            }
        });
    }

    public V send() {
        return atomic(new Callable<V>() {
            public V call() throws Exception {
                notifySendListeners();
                return immutableValue;
            }
        });
    }

    private void notifySetListeners(final V previousValue) {
        afterCommit(new Runnable() {
            public void run() {
                for(final Map.Entry<SetListener<V>, ListenerValue> entry : setListeners.entrySet()) {
                    final ListenerValue listenerValue = entry.getValue();
                    invoker.invoke(entry.getKey(), listenerValue, new SetEvent<V>(name, previousValue, immutableValue, listenerValue.nextInvocationCount()));
                }
            }
        });
    }

    private void notifySendListeners()
    {
        afterCommit(new Runnable() {
            public void run() {
                for(final Map.Entry<SendListener<V>, ListenerValue> entry : sendListeners.entrySet()) {
                    final ListenerValue listenerValue = entry.getValue();
                    invoker.invoke(entry.getKey(), listenerValue, new SendEvent<V>(name, immutableValue, listenerValue.nextInvocationCount()));
                }
            }
        });
    }

    public V get() {
        return atomic(new Callable<V>() {
            public V call() throws Exception {
                return immutableValue;
            }
        });
    }

    public void addSynchronousListener(final SetListener<V> listener)
    {
        atomic(new Runnable() {
            public void run() {
                setListeners.put(listener, new ListenerValue());
            }
        });
    }

    public void addListener(final SetListener<V> listener)
    {
        atomic(new Runnable() {
            public void run() {
                setListeners.put(listener, new ListenerValue());
            }
        });
    }

    public void addSynchronousListener(final SendListener<V> listener)
    {
        atomic(new Runnable() {
            public void run() {
                sendListeners.put(listener, new ListenerValue());
            }
        });
    }

    public void addListener(final SendListener<V> listener)
    {
        atomic(new Runnable() {
            public void run() {
                sendListeners.put(listener, new ListenerValue());
            }
        });
    }

    public boolean removeListener(final SetListener<V> listener)
    {
        return atomic(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return setListeners.remove(listener) != null;
            }
        });
    }

    public boolean removeListener(final SendListener<V> listener)
    {
        return atomic(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return sendListeners.remove(listener) != null;
            }
        });
    }

}
