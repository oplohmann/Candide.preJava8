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

import org.objectscape.candide.util.ImmutableEntry;
import org.objectscape.candide.util.ImmutableList;
import org.objectscape.candide.util.ImmutableSet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface ListenableConcurrentMap<K, V>
{
    int size();

    boolean isEmpty();

    boolean containsKey(Object key);

    boolean containsValue(Object value);

    ImmutableList<V> get(Object key);

    public V getSingleValue(Object key);

    ImmutableList<V> put(K key, ImmutableList<V> value);

    ImmutableList<V> remove(Object key);

    void putAll(Map<? extends K, ? extends ImmutableList<V>> map);

    void clear();

    Set<K> keySet();

    Collection<ImmutableList<V>> values();

    ImmutableSet<ImmutableEntry<K, ImmutableList<V>>> entrySet();

    void addAsynchronousListener(K key, PutListener<V> listener);

    void addAsynchronousListener(K key, PutListener<V> listener, boolean notifyWhenKeyPresent);

    void addSynchronousListener(K key, RemoveListener<V> listener);

    void addSynchronousListener(K key, SendListener<V> listener);

    void addSynchronousListener(K key, SendListener<V> listener, boolean notifyWhenKeyPresent);

    boolean removeListener(K key, PutListener<V> listener);

    boolean removeListener(K key, RemoveListener<V> listener);

    boolean removeListener(K key, SendListener<V> listener);

    int clearListeners();

    ImmutableList<V> putIfAbsent(K key, ImmutableList<V> value);

    ImmutableList<V> putIfAbsentSingleValue(K key, V value);

    ImmutableList<V> putIfAbsentOrIfEmpty(K key, ImmutableList<V> value);

    ImmutableList<V> putIfAbsentOrIfEmpty(K key, V value);

    public ImmutableList<V> putSingleValue(K key, V value);

    boolean remove(Object key, Object value);

    boolean replace(K key, ImmutableList<V> oldValue, ImmutableList<V> newValue);

    ImmutableList<V> replace(K key, ImmutableList<V> values);

    ImmutableList<V> replaceSingleValue(K key, V value);

    int send(K key);

    void addSynchronousListener(K key, PutListener<V> listener, boolean notifyWhenKeyPresent);

    void addAsynchronousListener(K key, SendListener<V> listener, boolean notifyWhenKeyPresent);

    void addAsynchronousListener(K key, SendListener<V> listener);

    void addAsynchronousListener(K key, RemoveListener<V> listener);

    void addSynchronousListener(K key, PutListener<V> listener);
}
