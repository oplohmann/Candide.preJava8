package org.objectscape.candide.util.scalastm;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Created with IntelliJ IDEA.
 * User: Oliver
 * Date: 24.10.13
 * Time: 18:43
 * To change this template use File | Settings | File Templates.
 */
public class AtomicUtils {

    public static void atomic(Runnable runnable) {
        scala.concurrent.stm.japi.STM.atomic(runnable);
    }

    public static <T> T atomic(Callable<T> callable) {
        return scala.concurrent.stm.japi.STM.atomic(callable);
    }

    public static <K, V> Map<K, V> newMap() {
        return scala.concurrent.stm.japi.STM.newMap();
    }

    public static <E> Set<E> newSet() {
        return scala.concurrent.stm.japi.STM.newSet();
    }
}
