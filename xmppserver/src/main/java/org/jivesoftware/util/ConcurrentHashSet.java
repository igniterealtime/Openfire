/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This class implements the {@code Set} interface, backed by a ConcurrentHashMap instance.
 *
 * @author Matt Tucker
 * @deprecated Use {@link ConcurrentHashMap#newKeySet()} instead.
 */
@Deprecated
public class ConcurrentHashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable,
        java.io.Serializable
{

    private transient ConcurrentMap<E,Object> map;

    // Dummy value to associate with an Object in the backing Map
    private static final Object PRESENT = new Object();

    /**
     * Constructs a new, empty set; the backing {@code ConcurrentHashMap} instance has
     * default initial capacity (16) and load factor (0.75).
     */
    public ConcurrentHashSet() {
        map = new ConcurrentHashMap<>();
    }

    /**
     * Constructs a new set containing the elements in the specified
     * collection.  The {@code ConcurrentHashMap} is created with default load factor
     * (0.75) and an initial capacity sufficient to contain the elements in
     * the specified collection.
     *
     * @param c the collection whose elements are to be placed into this set.
     * @throws NullPointerException   if the specified collection is null.
     */
    public ConcurrentHashSet(Collection<? extends E> c) {
        map = new ConcurrentHashMap<>(Math.max((int) (c.size()/.75f) + 1, 16));
        addAll(c);
    }

    /**
     * Constructs a new, empty set; the backing {@code ConcurrentHashMap} instance has
     * the specified initial capacity and the specified load factor.
     *
     * @param initialCapacity the initial capacity of the hash map.
     * @param loadFactor the load factor of the hash map.
     * @throws IllegalArgumentException if the initial capacity is less
     *      than zero, or if the load factor is nonpositive.
     */
    public ConcurrentHashSet(int initialCapacity, float loadFactor) {
        map = new ConcurrentHashMap<>(initialCapacity, loadFactor, 16);
    }

    /**
     * Constructs a new, empty set; the backing {@code HashMap} instance has
     * the specified initial capacity and default load factor, which is
     * {@code 0.75}.
     *
     * @param      initialCapacity   the initial capacity of the hash table.
     * @throws     IllegalArgumentException if the initial capacity is less
     *             than zero.
     */
    public ConcurrentHashSet(int initialCapacity) {
        map = new ConcurrentHashMap<>(initialCapacity);
    }

    @Override
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean add(E o) {
        return map.put(o, PRESENT)==null;
    }

    @Override
    public boolean remove(Object o) {
        return map.remove(o)==PRESENT;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Object clone() {
        try {
            ConcurrentHashSet<E> newSet = (ConcurrentHashSet<E>)super.clone();
            newSet.map.putAll(map);
            return newSet;
        }
        catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();

        // Write out size
        s.writeInt(map.size());

        // Write out all elements in the proper order.
        for (Iterator i=map.keySet().iterator(); i.hasNext(); )
            s.writeObject(i.next());
        }

    /**
     * Reconstitute the {@code HashSet} instance from a stream (that is,
     * deserialize it).
     * @param s the input stream
     * @throws java.io.IOException if an error occurs reading from the input stream
     * @throws ClassNotFoundException if the class on the input stream cannot be found
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException
    {
        s.defaultReadObject();

        map = new ConcurrentHashMap<>();

        // Read in size
        int size = s.readInt();

        // Read in all elements in the proper order.
        for (int i=0; i<size; i++) {
            E e = (E) s.readObject();
            map.put(e, PRESENT);
        }
    }
}
