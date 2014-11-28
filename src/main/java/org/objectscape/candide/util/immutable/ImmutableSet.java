package org.objectscape.candide.util.immutable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Oliver
 * Date: 07.06.13
 * Time: 08:21
 * To change this template use File | Settings | File Templates.
 */
public class ImmutableSet<T> {

    // assuming JDK5 memory model is used, which if fair since
    // generics also did not exist before JDK5
    private volatile Set<T> contents = new HashSet<>();

    public ImmutableSet() {
        super();
    }

    public ImmutableSet(T element) {
        super();
        contents.add(element);
    }

    public ImmutableSet(Collection<T> list) {
        super();
        contents.addAll(list);
    }

    public int size() {
        return contents.size();
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    public boolean contains(Object o) {
        return contents.contains(o);
    }

    public ImmutableIterator<T> iterator() {
        return new ImmutableIterator<T>(contents.iterator());
    }

    public Object[] toArray() {
        return contents.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return contents.toArray(a);
    }

    public Set<T> mutableSet() {
        return new HashSet<>(contents);
    }

    public boolean containsAll(Collection<?> c) {
        return contents.containsAll(c);
    }

    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (!(o instanceof Set))
            return false;

        return contents.equals((Set) o);
    }

    @Override
    public int hashCode() {
        return contents.hashCode();
    }

}
