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

import org.junit.Assert;
import org.junit.Test;
import org.objectscape.candide.common.SendEvent;
import org.objectscape.candide.common.SendListener;
import org.objectscape.candide.concurrent.ListenableConcurrentValue;
import org.objectscape.candide.concurrent.SetEvent;
import org.objectscape.candide.concurrent.SetListener;
import org.objectscape.candide.util.function.Function;
import org.objectscape.candide.util.values.BooleanValue;
import org.objectscape.candide.util.values.IntValue;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class ListenableConcurrentValueTest extends AbstractTest{

    @Test
    public void set()
    {
        ListenableConcurrentValue<Integer> valueHolder = new ListenableConcurrentValue("TestValueHolder", new Integer(0));
        boolean success = false;

        do {
            Integer currentValue = valueHolder.get();
            if(currentValue != null && currentValue.equals(new Integer(1))) {
                success = true;
                break;
            }
            success = valueHolder.set(currentValue, currentValue + 1);
        }
        while(!success);

        Assert.assertEquals(new Integer(1), valueHolder.get());
    }

    @Test
    public void setExpectWithFunction()
    {
        final int delta = new Random(System.currentTimeMillis()).nextInt();
        ListenableConcurrentValue<Integer> valueHolder = new ListenableConcurrentValue<Integer>("TestValueHolder", new Integer(0));
        boolean success = false;

        do {
            Integer currentValue = valueHolder.get();
            success = valueHolder.set(currentValue, new Function<Integer, Integer>() {
                public Integer apply(Integer i) {
                    return i + delta;
                }
            });
        }
        while(!success);

        Assert.assertEquals(new Integer(delta), valueHolder.get());
    }

    @Test
    public void setWithFunction()
    {
        final int delta = new Random(System.currentTimeMillis()).nextInt();
        final ListenableConcurrentValue<Integer> valueHolder = new ListenableConcurrentValue("TestValueHolder", new Integer(0));
        Integer newValue = null;

        Integer currentValue = valueHolder.get();
        newValue = valueHolder.set(new Function<Integer, Integer>() {
            public Integer apply(Integer i) {
                if(valueHolder.get() == 0)
                    return i + delta;
                return -1;
            }
        });

        Assert.assertEquals(new Integer(delta), valueHolder.get());
    }

    @Test
    public void setWithFunctionException()
    {
        final ListenableConcurrentValue<Integer> valueHolder = new ListenableConcurrentValue("TestValueHolder", new Integer(0));
        final BooleanValue listenerInvoked = new BooleanValue();
        valueHolder.addListener(new SetListener<Integer>() {
            public void accept(SetEvent<Integer> event) {
                listenerInvoked.set(true);
            }
        });

        try
        {
            valueHolder.set(new Function<Integer, Integer>() {
                public Integer apply(Integer i) {
                    if(true)
                        throw new RuntimeException("test exception");
                    return -1;
                }
            });
        }
        catch (RuntimeException e) {
            // common remained unchanged, because changes were rolled back when exception occurred
            Assert.assertEquals(new Integer(0), valueHolder.get());
        }

        Assert.assertFalse(listenerInvoked.get());
    }

    @Test
    public void setWithAynchronousSetListener() throws InterruptedException
    {
        final ListenableConcurrentValue<Integer> valueHolder = new ListenableConcurrentValue("TestValueHolder", new Integer(0));
        final IntValue previousValue = new IntValue(-1);
        final IntValue newValue = new IntValue(-1);
        final CountDownLatch latch = new CountDownLatch(1);

        valueHolder.addListener(new SetListener<Integer>() {
            public void accept(SetEvent<Integer> event) {
                previousValue.set(event.getPreviousValue());
                newValue.set(event.getValue());
                latch.countDown();
            }
        });

        valueHolder.set(new Function<Integer, Integer>() {
            public Integer apply(Integer i) {
                return 5;
            }
        });

        latch.await();
        Assert.assertEquals(new Integer(0), previousValue.getObject());
        Assert.assertEquals(new Integer(5), newValue.getObject());
    }

    @Test
    public void setWithSynchronousSetListener() throws InterruptedException
    {
        final ListenableConcurrentValue<Integer> valueHolder = new ListenableConcurrentValue("TestValueHolder", new Integer(0));
        final IntValue previousValue = new IntValue(-1);
        final IntValue newValue = new IntValue(-1);

        valueHolder.addSynchronousListener(new SetListener<Integer>() {
            public void accept(SetEvent<Integer> event) {
                previousValue.set(event.getPreviousValue());
                newValue.set(event.getValue());
            }
        });

        valueHolder.set(new Function<Integer, Integer>() {
            public Integer apply(Integer i) {
                return 5;
            }
        });

        Assert.assertEquals(new Integer(0), previousValue.getObject());
        Assert.assertEquals(new Integer(5), newValue.getObject());
    }

    @Test
    public void setWithSynchronousSendListener() throws InterruptedException {
        ListenableConcurrentValue<Integer> valueHolder = new ListenableConcurrentValue("TestValueHolder", new Integer(0));

        final IntValue newValue = new IntValue(-1);

        valueHolder.addSynchronousListener(new SendListener<Integer>() {
            public void accept(SendEvent<Integer> event) {
                newValue.set(event.getValue());
            }
        });

        valueHolder.set(new Function<Integer, Integer>() {
            public Integer apply(Integer i) {
                return i + 5;
            }
        });

        valueHolder.send();

        Assert.assertEquals(new Integer(5), newValue.getObject());
    }

    @Test
    public void setWithAsynchronousSendListener() throws InterruptedException
    {
        ListenableConcurrentValue<Integer> valueHolder = new ListenableConcurrentValue("TestValueHolder", new Integer(0));

        final IntValue newValue = new IntValue(-1);
        final CountDownLatch latch = new CountDownLatch(1);

        valueHolder.addListener(new SendListener<Integer>() {
            public void accept(SendEvent<Integer> event) {
                newValue.set(event.getValue());
                latch.countDown();
            }
        });

        valueHolder.set(new Function<Integer, Integer>() {
            public Integer apply(Integer i) {
                return i + 5;
            }
        });

        valueHolder.send();

        latch.await();
        Assert.assertEquals(new Integer(5), newValue.getObject());
    }
}
