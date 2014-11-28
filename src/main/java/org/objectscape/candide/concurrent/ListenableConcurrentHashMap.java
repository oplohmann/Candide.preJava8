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

import org.objectscape.candide.util.CallerMustSynchronize;
import org.objectscape.candide.util.ImmutableEntry;
import org.objectscape.candide.util.ImmutableList;
import org.objectscape.candide.util.ImmutableSet;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.objectscape.candide.util.ListUtils.*;

/**
 * 
 * The solution how to calculate segment offsets based on the key hash is taken
 * from Doug Lea's initial ConcurrentHashMap implementation:
 * http://www.java2s.com/Code/Java/Collections-Data-Structure/
 * AversionofHashtablesupportingconcurrencyforbothretrievalsandupdates.htm Note,
 * that this implementation is not the final one as included in JDK5 and later
 * which has slightly better performance, because it makes specific use of
 * Java's memory model.
 *
 * WARNING: This class is EXPERIMENTAL. It is based on segments which do locking entirely
 * based on ReentrantReadWriteLocks. This is contrary to the implementation by Doug Lea
 * which uses a smart combination of locks and CAS-based synchronization to achieve better
 * performance. For that reason this class will not offer the same performance and scalability
 * with an increasing number of threads.
 *
 * Note that all values are collections. The user only receives a copy of the common list, but never
 * the original. Any changes made in the returned copy will not affect the original collection.
 * Changes to the original collection will not result in some <code>ConcurrentModificationException</code>
 * (which is also not the case for <code>ConcurrentHashMap</code>).
 *
 * Certain operations like size(), keySet(), entrySet(), values() are costly as all map segments
 * are locked when they are executed resulting is high lock contention. The methods should therefore
 * be handled with care.
 *
 * * @author <a href="http://www.objectscape.org/">Oliver Plohmann</a>
 *
 */
public class ListenableConcurrentHashMap<K, V> implements Serializable, ListenableConcurrentMap<K,V> {

	/**
	 * Generated UID
	 */
	private static final long serialVersionUID = -3053115223732918946L;

    /**
     * The name of the map that is passed on with the <code>MapEvent</code> object to the
     * callback lambda.
     */
	private String mapName = null;

	/**
	 * The number of concurrency control segments. The common can be at most 32
	 * since ints are used as bitsets over segments. Emprically, it doesn't seem
	 * to pay to decrease it either, so the common should be at least 32. In
	 * other words, do not redefine this :-)
	 */
	protected static final int CONCURRENCY_LEVEL = 32;

	/**
	 * Mask common for indexing into segments
	 */
	protected static final int SEGMENT_MASK = CONCURRENCY_LEVEL - 1;

    /**
     * Array storing the CONCURRENCY_LEVEL number of <code>MapSegments</code> the map is divided into,
     * which can be accessed concurrently for certain map operations.
     */
	private MapSegment<K, V>[] mapSegments = null;

    /**
     * Constructs a new, empty ListenableConcurrentMap with <tt>CONCURRENCY_LEVEL</tt> number of segments.
     */
	public ListenableConcurrentHashMap() {
		super();
		init();
	}

    /**
     * Constructs a new, empty ListenableConcurrentMap with the map name <tt>mapName</tt>.
     */
	public ListenableConcurrentHashMap(String mapName) {
		super();
		this.mapName = mapName;
		init();
	}

    /**
     * Initialized the <code>ListenableConcurrentMap</code> by creating the map segments that for certain
     * operations can be accessed concurrently to reduce lock contention.
     */
	private void init()
	{
		mapSegments = new MapSegment[CONCURRENCY_LEVEL];
		for (int i = 0; i < mapSegments.length; i++)
			mapSegments[i] = new MapSegment<K, V>();
	}

	/**
	 * Return hash code for Object x. Since we are using power-of-two tables, it
	 * is worth the effort to improve hashcode via the same multiplicative
	 * scheme as used in IdentityHashMap.
	 * 
	 * Taken from Doug Lea's implementation.
	 * 
	 * @param key
	 * @return hash code
	 */
	protected static int hash(Object key)
	{
		int h = key.hashCode();
		// Multiply by 127 (quickly, via shifts), and mix in some high
		// bits to help guard against bunching of codes that are
		// consecutive or equally spaced.
		return ((h << 7) - h + (h >>> 9) + (h >>> 17));
	}

    /**
     * Return the applicable map segment for a given <tt>key</tt>
     *
     * @param key
     * @return the <code>MapSegment</code> for the given <tt>key</tt>
     */
	private MapSegment<K, V> getMapSegment(Object key)
	{
		if(key == null)
			throw new NullPointerException("key must not be null");
		return mapSegments[hash(key) & SEGMENT_MASK];
	}

    /**
     * Class that defines a map segment the ListenableConcurrentMap consists of. The purpose of the map
     * segments is to reduce lock contention and therefore improve performance in a multivalue-threaded
     * registry.
     *
     * @param <K> type of the key class
     * @param <V> type of the common class
     */
    @SuppressWarnings("hiding")
	private class MapSegment<K, V>
	{
		private Map<K, List<V>> map = new HashMap<K, List<V>>();

        private Map<K, Map<PutListener<V>, ListenerValue>> putListeners = new HashMap<K, Map<PutListener<V>, ListenerValue>>();
        private Map<K, Map<RemoveListener<V>, ListenerValue>> removeListeners = new HashMap<K, Map<RemoveListener<V>, ListenerValue>>();
        private Map<K, Map<SendListener<V>, ListenerValue>> sendListeners = new HashMap<K, Map<SendListener<V>, ListenerValue>>();

		private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

		@CallerMustSynchronize()
		public int size()
		{
			return map.size();
		}

		public boolean isEmpty()
		{
			return size() == 0;
		}

		public boolean containsKey(Object key)
		{
			lock.readLock().lock();

			try
			{
				return map.containsKey(key);
			} 
			finally
			{
				lock.readLock().unlock();
			}
		}

		@CallerMustSynchronize
		public boolean containsValue(Object value)
		{
			return map.containsValue(value);
		}

		public ImmutableList<V> get(Object key)
		{
			lock.readLock().lock();

			try
			{
				return immutableOrNull(map.get(key));
			} 
			finally
			{
				lock.readLock().unlock();
			}
		}

		public ImmutableList<V> put(K key, ImmutableList<V> values)
		{
			List<V> currentValues = map.get(key);
			if (currentValues == null)
			{
				map.put(key, values.mutableList());
				notifyPutListeners(key, values);
				return null;
			}

            ImmutableList<V> previousValues = immutable(currentValues);
			currentValues.addAll(values.mutableList());
			notifyPutListeners(key, values);
			return previousValues;
		}

		public ImmutableList<V> remove(Object key)
		{
			lock.writeLock().lock();

			try
			{
				List<V> values = map.remove(key);
				if(values != null) {
                    notifyRemoveListeners(key, immutable(values));
                    return immutable(values);
                }
				return null;
				
			} 
			finally
			{
				lock.writeLock().unlock();
			}
		}

		@CallerMustSynchronize
		public void clear()
		{
			map = new HashMap<K, List<V>>();
			putListeners = new HashMap<K, Map<PutListener<V>, ListenerValue>>();
			removeListeners = new HashMap<K, Map<RemoveListener<V>, ListenerValue>>();
			sendListeners = new HashMap<K, Map<SendListener<V>, ListenerValue>>();
		}

		@CallerMustSynchronize
		public Set<K> keySet()
		{
			return map.keySet();
		}

		@CallerMustSynchronize
		public Collection<List<V>> values()
		{
			return map.values();
		}

		@CallerMustSynchronize
		public Set<Map.Entry<K, List<V>>> entrySet()
		{
            for(Map.Entry<K, List<V>> entry : map.entrySet()) {

            }
			return map.entrySet();
		}

		public ImmutableList<V> putIfAbsent(K key, ImmutableList<V> valueList)
		{
			
			lock.writeLock().lock();

			try
			{
				List<V> values = map.get(key);
				if (values == null) {
					map.put(key, valueList.mutableList());
					notifyPutListeners(key, valueList);
					return null;
				}
								
				return immutable(values);
			} 
			finally
			{
				lock.writeLock().unlock();
			}
		}

		public ImmutableList<V> putIfAbsentSingleValue(K key, V value)
		{			
			return putIfAbsent(key, immutableList(value));
		}

		@SuppressWarnings("unchecked") // cast enforced by Map.rempove(Object, Object) interface
		public boolean remove(Object key, Object value)
		{
			lock.writeLock().lock();

			try
			{
				List<?> values = map.get(key);
				if (values == null)
					return false;
				boolean result = values.remove(value);
				notifyRemoveListeners(key, immutableList((V) value));
				return result;
			} 
			finally
			{
				lock.writeLock().unlock();
			}
		}

		public boolean replace(K key, ImmutableList<V> oldValues, ImmutableList<V> newValues)
		{
			lock.writeLock().lock();

			try
			{
				List<V> values = map.get(key);
				
				if(values != null && oldValues.equals(values))
				{
					notifyRemoveListeners(key, oldValues);
					map.put(key, newValues.mutableList());
					notifyPutListeners(key, newValues);
					return true;
				}
					
				return false;
			} 
			finally
			{
				lock.writeLock().unlock();
			}
		
		}

		public ImmutableList<V> replace(K key, ImmutableList<V> newValues)
		{
			lock.writeLock().lock();

			try
			{
				List<V> values = map.get(key);
				if(values == null)
					return null;

                ImmutableList<V> immutableValues = immutable(values);
				notifyRemoveListeners(key, immutableValues);
				map.put(key, newValues.mutableList());
				notifyPutListeners(key, immutableValues);
								
				return immutableValues;
			} 
			finally
			{
				lock.writeLock().unlock();
			}
		}

		@CallerMustSynchronize
		public ImmutableList<V> putSingleValue(K key, V value)
		{
            List<V> previousValues = map.get(key);
            map.put(key, list(value));
            notifyPutListeners(key, immutableList(value));

            if (previousValues == null)
                return null;

            return immutable(previousValues);
		}

		@CallerMustSynchronize
		private void notifyRemoveListeners(final Object key, final ImmutableList<V> removedValues)
		{
			Map<RemoveListener<V>, ListenerValue> listeners = removeListeners.get(key);
			if (listeners == null)
				return;

            for(final Map.Entry<RemoveListener<V>, ListenerValue> entry : listeners.entrySet()) {
                entry.getKey().accept(new RemoveEvent<V>(mapName, key, removedValues, entry.getValue().nextInvocationCount()));
            }
		}

        @CallerMustSynchronize
        private int notifySendListeners(final Object key)
        {
            Map<SendListener<V>, ListenerValue> listeners = sendListeners.get(key);
            if (listeners == null)
                return 0;

            for(final Map.Entry<SendListener<V>, ListenerValue> entry : listeners.entrySet()) {
                entry.getKey().accept(new SendEvent<V>(mapName, key, immutableOrNull(map.get(key)), entry.getValue().nextInvocationCount()));
            }

            return listeners.size();
        }

		@CallerMustSynchronize
		private void notifyPutListeners(final K key, final ImmutableList<V> putValues)
		{
            Map<PutListener<V>, ListenerValue> listeners = putListeners.get(key);
			if (listeners == null)
				return;

            for(final Map.Entry<PutListener<V>, ListenerValue> entry : listeners.entrySet()) {
                entry.getKey().accept(new PutEvent<V>(mapName, key, putValues, entry.getValue().nextInvocationCount()));
            }
		}

		public ImmutableList<V> putIfAbsentOrIfEmpty(K key, ImmutableList<V> values)
		{
			lock.writeLock().lock();

			try
			{
				List<V> currentValues = map.get(key);
				if(currentValues == null) {
					map.put(key, values.mutableList());
					notifyPutListeners(key, values);
					return null;
				}
				if (currentValues.isEmpty()) {
					currentValues.addAll(values.mutableList());
					notifyPutListeners(key, values);
					return null;
				}
				
				return immutable(currentValues);
			} 
			finally
			{
				lock.writeLock().unlock();
			}
		}
		
		public ImmutableList<V> putIfAbsentOrIfEmpty(K key, V value)
		{	
			return putIfAbsentOrIfEmpty(key, immutableList(value));
		}

		public ImmutableList<V> replaceSingleValue(K key, V value)
		{
			lock.writeLock().lock();

			try
			{
				List<V> values = map.get(key);
				if(values == null)
					return null;

                ImmutableList<V> immutableValues = immutableList(value);
				notifyRemoveListeners(key, immutableValues);
				map.put(key, list(value));
				notifyPutListeners(key, immutableValues);
				
				return immutableValues;
			} 
			finally
			{
				lock.writeLock().unlock();
			}
		}

        public void addListener(K key, PutListener<V> listener, boolean notifyWhenKeyPresent, ListenerValue value)
        {
            lock.writeLock().lock();

            try
            {
                Map<PutListener<V>, ListenerValue> listeners = putListeners.get(key);
                if(listeners == null) {
                    listeners = new HashMap<PutListener<V>, ListenerValue>();
                    putListeners.put(key, listeners);
                }

                listeners.put(listener, value);

                if(notifyWhenKeyPresent) {
                    List<V> values = map.get(key);
                    if(values != null)
                        notifyPutListeners(key, immutable(values));
                }
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        public boolean removeListener(K key, PutListener<V> listener)
        {
            lock.writeLock().lock();

            try
            {
                Map<PutListener<V>, ListenerValue> listeners = putListeners.get(key);
                if(listeners == null)
                    return false;

                boolean found = listeners.remove(listener) != null;

                if(listeners.isEmpty()) {
                    putListeners.remove(key);
                }

                return found;
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        public boolean removeListener(K key, RemoveListener<V> listener)
        {
            lock.writeLock().lock();

            try
            {
                Map<RemoveListener<V>, ListenerValue> listeners = removeListeners.get(key);
                if(listeners == null)
                    return false;

                boolean found = listeners.remove(listener) != null;

                if(listeners.isEmpty()) {
                    removeListeners.remove(key);
                }

                return found;
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

		public int send(K key)
		{
			lock.readLock().lock();

            try
			{
                return notifySendListeners(key);
			}
			finally
			{
				lock.readLock().unlock();
			}
		}

        public boolean removeListener(K key, SendListener<V> listener)
        {
            lock.writeLock().lock();

            try
            {
                Map<SendListener<V>, ListenerValue> listeners = sendListeners.get(key);
                if(listeners == null)
                    return false;

                boolean found = listeners.remove(listener) != null;

                if(listeners.isEmpty()) {
                    sendListeners.remove(key);
                }

                return found;
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        public V getSingleValue(Object key)
        {
            lock.readLock().lock();

            try
            {
                List<V> values = map.get(key);
                if(values == null)
                    return null;
                if(values.isEmpty())
                    return null;
                return values.get(0);
            }
            finally
            {
                lock.readLock().unlock();
            }
        }

        public void addListener(K key, RemoveListener<V> listener, ListenerValue value)
        {
            lock.writeLock().lock();

            try
            {
                Map<RemoveListener<V>, ListenerValue> listeners = removeListeners.get(key);
                if(listeners == null) {
                    listeners = new HashMap<RemoveListener<V>, ListenerValue>();
                    removeListeners.put(key, listeners);
                }
                listeners.put(listener, value);
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        public void addListener(K key, SendListener<V> listener, boolean notifyWhenKeyPresent, ListenerValue value)
        {
            lock.writeLock().lock();

            try
            {
                Map<SendListener<V>, ListenerValue> listeners = sendListeners.get(key);
                if(listeners == null) {
                    listeners = new HashMap<SendListener<V>, ListenerValue>();
                    sendListeners.put(key, listeners);
                }
                listeners.put(listener, value);
                if(notifyWhenKeyPresent && containsKey(key)) {
                    notifySendListeners(key);
                }
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        @CallerMustSynchronize
        public int clearListeners()
        {
            int size = putListeners.size();
            putListeners.clear();
            size += removeListeners.size();
            removeListeners.clear();
            size += sendListeners.size();
            sendListeners.clear();
            return size;
        }

        public void addListener(K key, PutListener<V> listener, ListenerValue value) {
            addListener(key, listener, false, value);
        }
    }

	public int size()
	{
		for (MapSegment<K, V> mapSegment : mapSegments) {
			mapSegment.lock.readLock().lock();
		}

		long size = 0;

		try
		{
			for (MapSegment<K, V> mapSegment : mapSegments)	{
				size += mapSegment.size();
			}
		} 
		finally
		{
			for (MapSegment<K, V> mapSegment : mapSegments) {
				mapSegment.lock.readLock().unlock();
			}
		}

		if(size > Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		
		if (size < 0) // overflow
			return Integer.MAX_VALUE;

		return (int) size;
	}

	public boolean isEmpty()
	{
        for (MapSegment<K, V> mapSegment : mapSegments) {
            mapSegment.lock.readLock().lock();
        }

        try
        {
            for (MapSegment<K, V> mapSegment : mapSegments)	{
                if(!mapSegment.isEmpty())
                    return false;
            }

            return true;
        }
        finally
        {
            for (MapSegment<K, V> mapSegment : mapSegments) {
                mapSegment.lock.readLock().unlock();
            }
        }
	}

	public boolean containsKey(Object key)
	{
		return getMapSegment(key).containsKey(key);
	}

	public boolean containsValue(Object value)
	{
		for (MapSegment<K, V> mapSegment : mapSegments)
		{
			mapSegment.lock.readLock().lock();
			
			try
			{
				if (mapSegment.containsValue(value))
					return true;
			} 
			finally
			{
				mapSegment.lock.readLock().unlock();
			}
		}

		return false;
	}

	public ImmutableList<V> get(Object key)
	{
		return getMapSegment(key).get(key);
	}

    public V getSingleValue(Object key)
    {
        return getMapSegment(key).getSingleValue(key);
    }

	public ImmutableList<V> put(K key, ImmutableList<V> value)
	{
		MapSegment<K, V> mapSegment = getMapSegment(key);
		mapSegment.lock.writeLock().lock();
		
		try
		{
			return mapSegment.put(key, value);
		} 
		finally
		{
			mapSegment.lock.writeLock().unlock();
		}
	}

	public ImmutableList<V> putSingleValue(K key, V value)
	{
		MapSegment<K, V> mapSegment = getMapSegment(key);
		mapSegment.lock.writeLock().lock();
		
		try
		{
			return mapSegment.putSingleValue(key, value);
		} 
		finally
		{
			mapSegment.lock.writeLock().unlock();
		}
	}

	public ImmutableList<V> remove(Object key)
	{
		return getMapSegment(key).remove(key);
	}

	public void putAll(Map<? extends K, ? extends ImmutableList<V>> map)
	{
		for(MapSegment<K, V> mapSegment : mapSegments) {
            mapSegment.lock.writeLock().lock();
        }

        try
        {
       	    for(Map.Entry<? extends K, ? extends ImmutableList<V>> entry : map.entrySet()) {
                getMapSegment(entry.getKey()).put(entry.getKey(), entry.getValue());
            }
        }
        finally
        {
            for(MapSegment<K, V> mapSegment : mapSegments) {
                mapSegment.lock.writeLock().unlock();
            }
        }
	}

	public void clear()
	{
		for (MapSegment<K, V> mapSegment : mapSegments)	{
			mapSegment.lock.writeLock().lock();
		}

		try
		{
			for (MapSegment<K, V> mapSegment : mapSegments)	{
				mapSegment.clear();
			}
		} 
		finally
		{
			for (MapSegment<K, V> mapSegment : mapSegments) {
				mapSegment.lock.writeLock().unlock();
			}
		}
	}

	public Set<K> keySet()
	{
		for (MapSegment<K, V> mapSegment : mapSegments)	{
			mapSegment.lock.readLock().lock();
		}

		Set<K> keySet = new HashSet<K>();

		try
		{
			for (MapSegment<K, V> mapSegment : mapSegments)	{
				keySet.addAll(mapSegment.keySet());
			}
		} 
		finally
		{
			for (MapSegment<K, V> mapSegment : mapSegments)	{
				mapSegment.lock.readLock().unlock();
			}
		}

		return keySet;
	}

	public Collection<ImmutableList<V>> values()
	{
		for (MapSegment<K, V> mapSegment : mapSegments)	{
			mapSegment.lock.readLock().lock();
		}

		Collection<ImmutableList<V>> values = new ArrayList<ImmutableList<V>>();

		try
		{
			for (MapSegment<K, V> mapSegment : mapSegments)	{
                List<V> allSegmentValues = new ArrayList<V>();
                for(List<V> segmentValues : mapSegment.values()) {
                    segmentValues.addAll(segmentValues);
                }
				values.add(immutable(allSegmentValues));
			}
		} 
		finally
		{
			for (MapSegment<K, V> mapSegment : mapSegments)	{
				mapSegment.lock.readLock().unlock();
			}
		}

		return values;
	}

	public ImmutableSet<ImmutableEntry<K, ImmutableList<V>>> entrySet()
	{
		for (MapSegment<K, V> mapSegment : mapSegments)	{
			mapSegment.lock.readLock().lock();
		}

        Set<ImmutableEntry<K, ImmutableList<V>>> entrySet = new HashSet<ImmutableEntry<K, ImmutableList<V>>>();

		try
		{
			for (MapSegment<K, V> mapSegment : mapSegments)	{
                for(Map.Entry<K, List<V>> entry : mapSegment.entrySet()) {
                    entrySet.add(new ImmutableEntry<K, ImmutableList<V>>(entry.getKey(), immutable(entry.getValue())));
                }
			}
            return new ImmutableSet<ImmutableEntry<K, ImmutableList<V>>>(entrySet);
        }
		finally
		{
            for (MapSegment<K, V> mapSegment : mapSegments)	{
                mapSegment.lock.readLock().unlock();
            }
        }

	}

    public void addAsynchronousListener(K key, PutListener<V> listener)	{
        addAsynchronousListener(key, listener, false);
    }

    public void addAsynchronousListener(K key, PutListener<V> listener, boolean notifyWhenKeyPresent)	{
        getMapSegment(key).addListener(key, listener, notifyWhenKeyPresent, new ListenerValue());
    }

    public void addSynchronousListener(K key, PutListener<V> listener, boolean notifyWhenKeyPresent)	{
        getMapSegment(key).addListener(key, listener, notifyWhenKeyPresent, new ListenerValue());
    }

    public void addSynchronousListener(K key, RemoveListener<V> listener) {
        getMapSegment(key).addListener(key, listener, new ListenerValue());
    }

    public void addAsynchronousListener(K key, RemoveListener<V> listener) {
        getMapSegment(key).addListener(key, listener, new ListenerValue());
    }

    public void addSynchronousListener(K key, PutListener<V> listener) {
        getMapSegment(key).addListener(key, listener, new ListenerValue());
    }

    public void addAsynchronousListener(K key, SendListener<V> listener) {
        addAsynchronousListener(key, listener, false);
    }

    public void addAsynchronousListener(K key, SendListener<V> listener, boolean notifyWhenKeyPresent) {
        getMapSegment(key).addListener(key, listener, notifyWhenKeyPresent, new ListenerValue());
    }

    public void addSynchronousListener(K key, SendListener<V> listener) {
        addSynchronousListener(key, listener, false);
    }

    public void addSynchronousListener(K key, SendListener<V> listener, boolean notifyWhenKeyPresent) {
        getMapSegment(key).addListener(key, listener, notifyWhenKeyPresent, new ListenerValue());
    }

    public boolean removeListener(K key, PutListener<V> listener) {
        return getMapSegment(key).removeListener(key, listener);
    }

    public boolean removeListener(K key, RemoveListener<V> listener) {
        return getMapSegment(key).removeListener(key, listener);
    }

    public boolean removeListener(K key, SendListener<V> listener) {
        return getMapSegment(key).removeListener(key, listener);
    }

	public ImmutableList<V> putIfAbsent(K key, ImmutableList<V> value) {
		return getMapSegment(key).putIfAbsent(key, value);
	}

    public ImmutableList<V> putIfAbsentSingleValue(K key, V value) {
		return getMapSegment(key).putIfAbsentSingleValue(key, value);
	}

    public ImmutableList<V> putIfAbsentOrIfEmpty(K key, ImmutableList<V> value) {
		return getMapSegment(key).putIfAbsentOrIfEmpty(key, value);
	}

    public ImmutableList<V> putIfAbsentOrIfEmpty(K key, V value) {
		return getMapSegment(key).putIfAbsentOrIfEmpty(key, value);
	}

	public boolean remove(Object key, Object value) {
		return getMapSegment(key).remove(key, value);
	}

	public boolean replace(K key, ImmutableList<V> oldValue, ImmutableList<V> newValue) {
		return getMapSegment(key).replace(key, oldValue, newValue);
	}

	public ImmutableList<V> replace(K key, ImmutableList<V> values) {
		return getMapSegment(key).replace(key, values);
	}

	/**
	 * Utility method not required by ConcurrentMap.
	 *  
	 * @param key
	 * @param value
	 * @return
	 */
    public ImmutableList<V> replaceSingleValue(K key, V value) {
		return getMapSegment(key).replaceSingleValue(key, value);
	}

    public int send(K key) {
		return getMapSegment(key).send(key);
	}

    public int clearListeners()
    {
        for (MapSegment<K, V> mapSegment : mapSegments)	{
            mapSegment.lock.writeLock().lock();
        }

        int listenersCount = 0;

        try
        {
            for (MapSegment<K, V> mapSegment : mapSegments)	{
                listenersCount += mapSegment.clearListeners();
            }
        }
        finally
        {
            for (MapSegment<K, V> mapSegment : mapSegments)	{
                mapSegment.lock.writeLock().unlock();
            }
        }

        return listenersCount;
    }

}
