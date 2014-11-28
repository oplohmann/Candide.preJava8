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
package org.objectscape.candide.concurrent;

import org.objectscape.candide.common.SendListener;
import org.objectscape.candide.util.CallerMustSynchronize;
import org.objectscape.candide.util.function.Function;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ListenableConcurrentValue<V> {

    protected String name = null;
    protected V value = null;
    protected ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    Map<SetListener<V>, ListenerValue> setListeners = new HashMap<SetListener<V>, ListenerValue>();
    Map<org.objectscape.candide.common.SendListener<V>, ListenerValue> sendListeners = new HashMap<org.objectscape.candide.common.SendListener<V>, ListenerValue>();

    public ListenableConcurrentValue() {
        super();
    }

    public ListenableConcurrentValue(String name) {
        super();
        this.name = name;
    }

    public ListenableConcurrentValue(V value) {
        super();
        this.value = value;
    }

    public ListenableConcurrentValue(String name, V value) {
        super();
        this.name = name;
        this.value = value;
    }

    public boolean set(V expectedValue, V newValue)
    {
        lock.writeLock().lock();
        try {
            if(value == null && expectedValue != null)
                return false;
            if(!value.equals(expectedValue))
                return false;
            V previousValue = value;
            this.value = newValue;
            notifySetListeners(previousValue);
            return true;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean set(V expectedValue, Function<V, V> function)
    {
        lock.writeLock().lock();
        try {
            if(value == null && expectedValue != null)
                return false;
            if(!value.equals(expectedValue))
                return false;
            V previousValue = value;
            this.value = function.apply(value);
            notifySetListeners(previousValue);
            return true;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public V set(Function<V, V> function)
    {
        lock.writeLock().lock();
        try {
            V previousValue = value;
            this.value = function.apply(value);
            notifySetListeners(previousValue);
            return value;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public V get()
    {
        lock.readLock().lock();
        try {
            return value;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @CallerMustSynchronize
    protected void notifySetListeners(V previousValue) {
        if(previousValue != null && previousValue.equals(value))
            return;
        else if(previousValue == value)
            return;
        notifySetListeners(new SetEvent<V>(name, previousValue, value));
    }

    @CallerMustSynchronize
    protected void notifySendListeners(final org.objectscape.candide.common.SendEvent<V> event) {
        for(final Map.Entry<org.objectscape.candide.common.SendListener<V>, ListenerValue> entry : sendListeners.entrySet()) {
            entry.getKey().accept(new org.objectscape.candide.common.SendEvent<V>(event, entry.getValue().nextInvocationCount()));
        }
    }

    @CallerMustSynchronize
    protected void notifySetListeners(final SetEvent<V> event) {
        for(final Map.Entry<SetListener<V>, ListenerValue> entry : setListeners.entrySet()) {
            entry.getKey().accept(new SetEvent<V>(event, entry.getValue().nextInvocationCount()));
        }
    }

    @CallerMustSynchronize
    protected void notifySendListeners() {
        notifySendListeners(new org.objectscape.candide.common.SendEvent<V>(name, value));
    }

    public V send()
    {
        lock.readLock().lock();
        try {
            notifySendListeners();
            return value;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addListener(SetListener<V> listener)
    {
        lock.writeLock().lock();

        try
        {
            setListeners.put(listener, new ListenerValue());
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public void addSynchronousListener(SetListener<V> listener)
    {
        lock.writeLock().lock();

        try
        {
            setListeners.put(listener, new ListenerValue());
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public void addListener(org.objectscape.candide.common.SendListener<V> listener)
    {
        lock.writeLock().lock();

        try
        {
            sendListeners.put(listener, new ListenerValue());
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public void addSynchronousListener(org.objectscape.candide.common.SendListener<V> listener)
    {
        lock.writeLock().lock();

        try
        {
            sendListeners.put(listener, new ListenerValue());
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public boolean removeListener(SetListener<V> listener)
    {
        lock.writeLock().lock();

        try
        {
            return setListeners.remove(listener) != null;
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public boolean removeListener(SendListener<V> listener)
    {
        lock.writeLock().lock();

        try
        {
            return sendListeners.remove(listener) != null;
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

}
