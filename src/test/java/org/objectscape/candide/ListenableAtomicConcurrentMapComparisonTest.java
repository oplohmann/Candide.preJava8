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

package org.objectscape.candide;

import org.junit.Ignore;
import org.junit.Test;
import org.objectscape.candide.concurrent.ListenableConcurrentHashMap;
import org.objectscape.candide.concurrent.ListenableConcurrentMap;
import org.objectscape.candide.stm.ListenableAtomicMap;
import org.objectscape.candide.util.scalastm.AtomicUtils;
import org.objectscape.candide.util.values.IntValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

import static org.objectscape.candide.util.scalastm.AtomicUtils.atomic;
import static org.objectscape.candide.util.scalastm.AtomicUtils.newMap;

/**
 * Result of running <code>putConcurrentMap</code>
 *
 * time for 2 threads concurrent: 1947 ms
 * time for 4 threads concurrent: 3655 ms
 * time for 6 threads concurrent: 6301 ms
 * time for 8 threads concurrent: 5959 ms
 * time for 10 threads concurrent: 9041 ms
 * time for 12 threads concurrent: 8969 ms
 * time for 14 threads concurrent: 11864 ms
 * time for 16 threads concurrent: 12127 ms
 *
 * Result of running <code>putConcurrentListenableMap</code>:
 *
 * time for 2 threads concurrent: 8642 ms
 * time for 4 threads concurrent: 15254 ms
 * time for 6 threads concurrent: 22461 ms
 * time for 8 threads concurrent: 30004 ms
 * time for 10 threads concurrent: 37884 ms
 * time for 12 threads concurrent: 45310 ms
 * time for 14 threads concurrent: 52849 ms
 * time for 16 threads concurrent: 60464 ms
 *
 * Result of running <code>putAtomicMap</code>:
 *
 * time for 2 threads scalastm: 4382 ms
 * time for 4 threads scalastm: 8514 ms
 * time for 6 threads scalastm: 12216 ms
 * time for 8 threads scalastm: 15919 ms
 * time for 10 threads scalastm: 19951 ms
 * time for 12 threads scalastm: 23773 ms
 * time for 14 threads scalastm: 27640 ms
 * time for 16 threads scalastm: 31873 ms
 *
 * Result of running <code>putListenableAtomicMap</code>:
 *
 * time for 2 threads scalastm: 6883 ms
 * time for 4 threads scalastm: 13158 ms
 * time for 6 threads scalastm: 21214 ms
 * time for 8 threads scalastm: 25690 ms
 * time for 10 threads scalastm: 31089 ms
 * time for 12 threads scalastm: 37744 ms
 * time for 14 threads scalastm: 43957 ms
 * time for 16 threads scalastm: 51935 ms
 *
 */

@Ignore // not part of regression tests - for performance comparison only
public class ListenableAtomicConcurrentMapComparisonTest extends AbstractTest
{

    private int max = 9000000;
    private int maxThreads = 16;

    @Test
    public void putConcurrentMap() throws InterruptedException
    {
        for(int i = 1; i * 2 <= maxThreads; i++) {
            putConcurrentMap(i * 2, max);
        }
    }

    @Test
    public void putConcurrentListenableMap() throws InterruptedException
    {
        for(int i = 1; i * 2 <= maxThreads; i++) {
            putConcurrentListenableMap(i * 2, max);
        }
    }

    @Test
    public void putAtomicMap() throws InterruptedException
    {
        for(int i = 1; i * 2 <= maxThreads; i++) {
            putAtomicMap(i * 2, max);
        }
    }

    @Test
    public void putListenableAtomicMap() throws InterruptedException
    {
        for(int i = 1; i * 2 <= maxThreads; i++) {
            putListenableAtomicMap(i * 2, max);
        }
    }

    private void putAtomicMap(int numThreads, int max) throws InterruptedException
    {
        CountDownLatch allDone = new CountDownLatch(numThreads);
        Map<String, String> map = newMap();
        List<Thread> threads = new ArrayList<>(numThreads);

        for (int i = 0; i < numThreads; i++)
            threads.add(new Thread(atomicPutBlock(map, max, allDone)));

        long start = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++)
            threads.get(i).start();

        allDone.await();

        System.out.println("time for " + numThreads + " threads scalastm: " + (System.currentTimeMillis() - start) + " ms");
    }

    private Runnable atomicPutBlock(final Map<String, String> map, final int max, final CountDownLatch done)
    {
        return new Runnable() {
            public void run() {
                final IntValue count = new IntValue();

                while(count.get() < max) {
                    AtomicUtils.atomic(new Runnable() {
                        public void run() {
                            map.put("1", "1");
                            count.increment();
                        }
                    });
                }
                done.countDown();
            }
        };
    }

    private void putListenableAtomicMap(int numThreads, int max) throws InterruptedException
    {
        CountDownLatch allDone = new CountDownLatch(numThreads);
        ListenableAtomicMap<String, String> map = new ListenableAtomicMap<>();
        List<Thread> threads = new ArrayList<>(numThreads);

        for (int i = 0; i < numThreads; i++)
            threads.add(new Thread(listenableAtomicPutBlock(map, max, allDone)));

        long start = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++)
            threads.get(i).start();

        allDone.await();

        System.out.println("time for " + numThreads + " threads scalastm: " + (System.currentTimeMillis() - start) + " ms");
    }

    private void putConcurrentListenableMap(int numThreads, int max) throws InterruptedException
    {
        CountDownLatch allDone = new CountDownLatch(numThreads);
        ListenableConcurrentMap<String, String> map = new ListenableConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>(numThreads);

        for (int i = 0; i < numThreads; i++)
            threads.add(new Thread(listenableConcurrentPutBlock(map, max, allDone)));

        long start = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++)
            threads.get(i).start();

        allDone.await();

        System.out.println("time for " + numThreads + " threads concurrent: " + (System.currentTimeMillis() - start) + " ms");
    }

    private void putConcurrentMap(int numThreads, int max) throws InterruptedException
    {
        CountDownLatch allDone = new CountDownLatch(numThreads);
        ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>(numThreads);

        for (int i = 0; i < numThreads; i++)
            threads.add(new Thread(concurrentPutBlock(map, max, allDone)));

        long start = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++)
            threads.get(i).start();

        allDone.await();

        System.out.println("time for " + numThreads + " threads concurrent: " + (System.currentTimeMillis() - start) + " ms");
    }

    private Runnable concurrentPutBlock(final ConcurrentMap<String, String> map, final int max, final CountDownLatch done)
    {
        return new Runnable()
        {
            public void run() {
                int count = 0;

                while(count < max) {
                    map.put("1", "1");
                    count++;
                }

                done.countDown();
            }
        };
    }

    private  Runnable listenableConcurrentPutBlock(final ListenableConcurrentMap<String, String> map, final int max, final CountDownLatch done)
    {
        return new Runnable() {
            public void run() {
                int count = 0;

                while(count < max) {
                    map.putSingleValue("1", "1");
                    count++;
                }

                done.countDown();
            }
        };
    }

    private  Runnable listenableAtomicPutBlock(final ListenableAtomicMap<String, String> map, final int max, final CountDownLatch done)
    {
        return new Runnable()
        {
            public void run()
            {
                final IntValue count = new IntValue();

                while(count.get() < max) {
                    atomic(new Runnable() {
                        public void run() {
                            map.put("1", "1");
                            count.increment();
                        }
                    });
                }
                done.countDown();
            }
        };
    }

}
