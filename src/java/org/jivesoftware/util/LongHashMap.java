/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * This class was adapted directly from the Colt sources by CoolServlets Inc.
 * The changes involved modifying the code so that the functionality could be
 * encapsulated in a single class file.
 *
 * As such, the original copyright is left intact and this file is distributed
 * under the original Colt license as seen below. Please visit the Colt
 * homepage for more information about the excellent package:
 * http://tilde-hoschek.home.cern.ch/~hoschek/colt/index.htm
 * ---------------------------------------------------------------------------
 * Copyright © 1999 CERN - European Organization for Nuclear Research.
 * Permission to use, copy, modify, distribute and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting documentation. CERN
 * makes no representations about the suitability of this software for any
 * purpose. It is provided "as is" without expressed or implied warranty.
 */

package org.jivesoftware.util;

/**
 * Hash map holding (key,value) associations of type <tt>(long-->Object)</tt>;
 * Automatically grows and shrinks as needed; Implemented using open addressing
 * with double hashing.<p>
 * <p/>
 * Adapted from the Colt package by CoolServlets. Please visit the Colt
 * homepage at: http://tilde-hoschek.home.cern.ch/~hoschek/colt/index.htm
 *
 * @author wolfgang.hoschek@cern.ch
 */
public final class LongHashMap {

    //The hash table keys.
    protected long table[];
    //The hash table values.
    protected Object values[];
    //The state of each hash table entry (FREE, FULL, REMOVED).
    protected byte state[];
    //The number of table entries in state==FREE.
    protected int freeEntries;

    //The number of distinct associations in the map; its "size()".
    protected int distinct;

    /**
     * The table capacity c=table.length always satisfies the invariant
     * <tt>c * minLoadFactor <= s <= c * maxLoadFactor</tt>, where s=size() is
     * the number of associations currently contained. The term
     * "c * minLoadFactor" is called the "lowWaterMark", "c * maxLoadFactor" is
     * called the "highWaterMark". In other words, the table capacity (and
     * proportionally the memory used by this class) oscillates within these
     * constraints. The terms are precomputed and cached to avoid recalculating
     * them each time put(..) or removeKey(...) is called.
     */
    protected int lowWaterMark;
    protected int highWaterMark;

    //The minimum load factor for the hashtable.
    protected double minLoadFactor;
    //The maximum load factor for the hashtable.
    protected double maxLoadFactor;

    protected static final int DEFAULT_CAPACITY = 277;
    protected static final double DEFAULT_MIN_LOAD_FACTOR = 0.2;
    protected static final double DEFAULT_MAX_LOAD_FACTOR = 0.6;

    protected static final byte FREE = 0;
    protected static final byte FULL = 1;
    protected static final byte REMOVED = 2;

    /**
     * Constructs an empty map with default capacity and default load factors.
     */
    public LongHashMap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Constructs an empty map with the specified initial capacity and default
     * load factors.
     *
     * @param initialCapacity the initial capacity of the map.
     * @throws IllegalArgumentException if the initial capacity is less
     *                                  than zero.
     */
    public LongHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_MIN_LOAD_FACTOR, DEFAULT_MAX_LOAD_FACTOR);
    }

    /**
     * Constructs an empty map with the specified initial capacity and the
     * specified minimum and maximum load factor.
     *
     * @param initialCapacity the initial capacity.
     * @param minLoadFactor   the minimum load factor.
     * @param maxLoadFactor   the maximum load factor.
     * @throws	IllegalArgumentException if <tt>initialCapacity < 0 ||
     * (minLoadFactor < 0.0 || minLoadFactor >= 1.0) ||
     * (maxLoadFactor <= 0.0 || maxLoadFactor >= 1.0) ||
     * (minLoadFactor >= maxLoadFactor)</tt>.
     */
    public LongHashMap(int initialCapacity, double minLoadFactor, double maxLoadFactor) {
        setUp(initialCapacity, minLoadFactor, maxLoadFactor);
    }

    /**
     * Removes all (key,value) associations from the receiver.
     * Implicitly calls <tt>trimToSize()</tt>.
     */
    public void clear() {
        for (int i = 0; i < state.length; i++) {
            state[i] = FREE;
        }
        for (int i = 0; i < values.length - 1; i++) {
            values[i] = null;
        }

        this.distinct = 0;
        this.freeEntries = table.length; // delta
        trimToSize();
    }

    /**
     * Returns <tt>true</tt> if the receiver contains the specified key.
     *
     * @return <tt>true</tt> if the receiver contains the specified key.
     */
    public boolean containsKey(long key) {
        return indexOfKey(key) >= 0;
    }

    /**
     * Returns <tt>true</tt> if the receiver contains the specified value.
     *
     * @return <tt>true</tt> if the receiver contains the specified value.
     */
    public boolean containsValue(Object value) {
        return indexOfValue(value) >= 0;
    }

    /**
     * Ensures that the receiver can hold at least the specified number of
     * associations without needing to allocate new internal memory. If
     * necessary, allocates new internal memory and increases the capacity of
     * the receiver.<p>
     * <p/>
     * This method never need be called; it is for performance tuning only.
     * Calling this method before <tt>put()</tt>ing a large number of
     * associations boosts performance, because the receiver will grow only
     * once instead of potentially many times and hash collisions get less
     * probable.
     *
     * @param minCapacity the desired minimum capacity.
     */
    public void ensureCapacity(int minCapacity) {
        if (table.length < minCapacity) {
            int newCapacity = nextPrime(minCapacity);
            rehash(newCapacity);
        }
    }

    /**
     * Returns the value associated with the specified key.
     * It is often a good idea to first check with {@link #containsKey(long)}
     * whether the given key has a value associated or not, i.e. whether there
     * exists an association for the given key or not.
     *
     * @param key the key to be searched for.
     * @return the value associated with the specified key; <tt>null</tt> if no
     *         such key is present.
     */
    public final Object get(long key) {
        int i = indexOfKey(key);
        //If not in the map return null
        if (i < 0) {
            return null;
        }
        else {
            return values[i];
        }
    }

    /**
     * Returns the index where the key would need to be inserted, if it is not
     * already contained. Returns -index-1 if the key is already contained
     * at slot index. Therefore, if the returned index < 0, then it is
     * already contained at slot -index-1. If the returned index >= 0,
     * then it is NOT already contained and should be inserted at slot index.
     *
     * @param key the key to be added to the receiver.
     * @return the index where the key would need to be inserted.
     */
    private final int indexOfInsertion(long key) {
        final long tab[] = table;
        final byte stat[] = state;
        final int length = tab.length;

        final int hash = ((int)(key ^ (key >> 32))) & 0x7FFFFFFF;
        int i = hash % length;
        // double hashing, see http://www.eece.unm.edu/faculty/heileman/hash/node4.html
        int decrement = (hash) % (length - 2);
        //OLD CODE: int decrement = (hash / length) % length;
        if (decrement == 0) decrement = 1;

        // stop if we find a removed or free slot, or if we find the key itself
        // do NOT skip over removed slots (yes, open addressing is like that...)
        while (stat[i] == FULL && tab[i] != key) {
            i -= decrement;
            //hashCollisions++;
            if (i < 0) i += length;
        }

        if (stat[i] == REMOVED) {
            // stop if we find a free slot, or if we find the key itself.
            // do skip over removed slots (yes, open addressing is like that...)
            // assertion: there is at least one FREE slot.
            int j = i;
            while (stat[i] != FREE && (stat[i] == REMOVED || tab[i] != key)) {
                i -= decrement;
                //hashCollisions++;
                if (i < 0) i += length;
            }
            if (stat[i] == FREE) i = j;
        }

        if (stat[i] == FULL) {
            // key already contained at slot i.
            // return a negative number identifying the slot.
            return -i - 1;
        }
        // not already contained, should be inserted at slot i.
        // return a number >= 0 identifying the slot.
        return i;
    }

    /**
     * @param key the key to be searched in the receiver.
     * @return the index where the key is contained in the receiver, returns -1
     *         if the key was not found.
     */
    private final int indexOfKey(long key) {
        final long tab[] = table;
        final byte stat[] = state;
        final int length = tab.length;

        final int hash = ((int)(key ^ (key >> 32))) & 0x7FFFFFFF;
        int i = hash % length;
        // double hashing, see http://www.eece.unm.edu/faculty/heileman/hash/node4.html
        int decrement = (hash) % (length - 2);
        //OLD CODE: int decrement = (hash / length) % length;
        if (decrement == 0) decrement = 1;

        // stop if we find a free slot, or if we find the key itself.
        // do skip over removed slots (yes, open addressing is like that...)
        while (stat[i] != FREE && (stat[i] == REMOVED || tab[i] != key)) {
            i -= decrement;
            //hashCollisions++;
            if (i < 0) i += length;
        }

        if (stat[i] == FREE) return -1; // not found
        return i; //found, return index where key is contained
    }


    /**
     * @param value the value to be searched in the receiver.
     * @return the index where the value is contained in the receiver,
     *         returns -1 if the value was not found.
     */
    protected int indexOfValue(Object value) {
        final Object val[] = values;
        final byte stat[] = state;

        for (int i = stat.length; --i >= 0;) {
            if (stat[i] == FULL && val[i] == value) return i;
        }

        return -1; // not found
    }

    /**
     * Returns the first key the given value is associated with.
     *
     * @param value the value to search for.
     * @return the first key for which holds <tt>get(key) == value</tt>;
     *         returns <tt>Long.MIN_VALUE</tt> if no such key exists.
     */
    public long keyOf(Object value) {
        //returns the first key found; there may be more matching keys, however.
        int i = indexOfValue(value);
        if (i < 0) return Long.MIN_VALUE;
        return table[i];
    }

    /**
     * Returns all the keys in the map.
     */
    public long[] keys() {
        long[] elements = new long[distinct];
        long[] tab = table;
        byte[] stat = state;
        int j = 0;
        for (int i = tab.length; i-- > 0;) {
            if (stat[i] == FULL) {
                elements[j++] = tab[i];
            }
        }
        return elements;
    }

    /**
     * Associates the given key with the given value. Replaces any old
     * <tt>(key,someOtherValue)</tt> association, if existing.
     *
     * @param key   the key the value shall be associated with.
     * @param value the value to be associated.
     * @return <tt>true</tt> if the receiver did not already contain such a key;
     *         <tt>false</tt> if the receiver did already contain such a key - the
     *         new value has now replaced the formerly associated value.
     */
    public boolean put(long key, Object value) {
        int i = indexOfInsertion(key);
        if (i < 0) { //already contained
            i = -i - 1;
            this.values[i] = value;
            return false;
        }

        if (this.distinct > this.highWaterMark) {
            int newCapacity = chooseGrowCapacity(this.distinct + 1,
                    this.minLoadFactor,
                    this.maxLoadFactor);
            rehash(newCapacity);
            return put(key, value);
        }

        this.table[i] = key;
        this.values[i] = value;
        if (this.state[i] == FREE) this.freeEntries--;
        this.state[i] = FULL;
        this.distinct++;

        if (this.freeEntries < 1) { //delta
            int newCapacity = chooseGrowCapacity(this.distinct + 1,
                    this.minLoadFactor,
                    this.maxLoadFactor);
            rehash(newCapacity);
        }

        return true;
    }

    /**
     * Returns the number of (key,value) associations currently contained.
     *
     * @return the number of (key,value) associations currently contained.
     */
    public int size() {
        return distinct;
    }

    /**
     * Returns <tt>true</tt> if the receiver contains no (key,value) associations.
     *
     * @return <tt>true</tt> if the receiver contains no (key,value) associations.
     */
    public boolean isEmpty() {
        return distinct == 0;
    }

    /**
     * Rehashes the contents of the receiver into a new table
     * with a smaller or larger capacity.
     * This method is called automatically when the
     * number of keys in the receiver exceeds the high water mark or falls
     * below the low water mark.
     */
    protected void rehash(int newCapacity) {
        int oldCapacity = table.length;
        //if (oldCapacity == newCapacity) return;

        long oldTable[] = table;
        Object oldValues[] = values;
        byte oldState[] = state;

        long newTable[] = new long[newCapacity];
        Object newValues[] = new Object[newCapacity];
        byte newState[] = new byte[newCapacity];

        this.lowWaterMark = chooseLowWaterMark(newCapacity, this.minLoadFactor);
        this.highWaterMark = chooseHighWaterMark(newCapacity, this.maxLoadFactor);

        this.table = newTable;
        this.values = newValues;
        this.state = newState;
        this.freeEntries = newCapacity - this.distinct; // delta

        for (int i = oldCapacity; i-- > 0;) {
            if (oldState[i] == FULL) {
                long element = oldTable[i];
                int index = indexOfInsertion(element);
                newTable[index] = element;
                newValues[index] = oldValues[i];
                newState[index] = FULL;
            }
        }
    }

    /**
     * Removes the given key with its associated element from the receiver, if
     * present.
     *
     * @param key the key to be removed from the receiver.
     * @return <tt>true</tt> if the receiver contained the specified key,
     *         <tt>false</tt> otherwise.
     */
    public boolean removeKey(long key) {
        int i = indexOfKey(key);
        if (i < 0) return false; // key not contained

        this.state[i] = REMOVED;
        this.values[i] = null; // delta
        this.distinct--;

        if (this.distinct < this.lowWaterMark) {
            int newCapacity = chooseShrinkCapacity(this.distinct,
                    this.minLoadFactor,
                    this.maxLoadFactor);
            rehash(newCapacity);
        }

        return true;
    }

    /**
     * Initializes the receiver.
     *
     * @param initialCapacity the initial capacity of the receiver.
     * @param minLoadFactor   the minLoadFactor of the receiver.
     * @param maxLoadFactor   the maxLoadFactor of the receiver.
     * @throws	IllegalArgumentException if <tt>initialCapacity < 0 ||
     * (minLoadFactor < 0.0 || minLoadFactor >= 1.0) ||
     * (maxLoadFactor <= 0.0 || maxLoadFactor >= 1.0) ||
     * (minLoadFactor >= maxLoadFactor)</tt>.
     */
    protected void setUp(int initialCapacity, double minLoadFactor, double maxLoadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial Capacity must not be less than zero: " + initialCapacity);
        }
        if (minLoadFactor < 0.0 || minLoadFactor >= 1.0) {
            throw new IllegalArgumentException("Illegal minLoadFactor: " + minLoadFactor);
        }
        if (maxLoadFactor <= 0.0 || maxLoadFactor >= 1.0) {
            throw new IllegalArgumentException("Illegal maxLoadFactor: " + maxLoadFactor);
        }
        if (minLoadFactor >= maxLoadFactor) {
            throw new IllegalArgumentException("Illegal minLoadFactor: " + minLoadFactor +
                    " and maxLoadFactor: " + maxLoadFactor);
        }

        int capacity = initialCapacity;
        capacity = nextPrime(capacity);
        // open addressing needs at least one FREE slot at any time.
        if (capacity == 0) {
            capacity = 1;
        }

        this.table = new long[capacity];
        this.values = new Object[capacity];
        this.state = new byte[capacity];

        //memory will be exhausted long before this pathological case happens, anyway.
        this.minLoadFactor = minLoadFactor;
        if (capacity == LARGEST_PRIME)
            this.maxLoadFactor = 1.0;
        else
            this.maxLoadFactor = maxLoadFactor;

        this.distinct = 0;
        this.freeEntries = capacity; // delta

        /**
         * lowWaterMark will be established upon first expansion. Establishing
         * it now (upon instance construction) would immediately make the table
         * shrink upon first put(...). After all the idea of an
         * "initialCapacity" implies violating lowWaterMarks when an object is
         * young. See ensureCapacity(...)
         */
        this.lowWaterMark = 0;
        this.highWaterMark = chooseHighWaterMark(capacity, this.maxLoadFactor);
    }

    /**
     * Trims the capacity of the receiver to be the receiver's current size.
     * Releases any superfluous internal memory. An application can use this
     * operation to minimize the storage of the receiver.
     */
    public void trimToSize() {
        //*1.2 because open addressing's performance exponentially degrades
        //beyond that point so that even rehashing the table can take very long
        int newCapacity = nextPrime((int)(1 + 1.2 * size()));
        if (table.length > newCapacity) {
            rehash(newCapacity);
        }
    }

    /**
     * Returns an array of all the values in the Map.
     */
    public Object[] values() {
        Object[] elements = new Object[distinct];

        Object[] val = values;
        byte[] stat = state;

        int j = 0;
        for (int i = stat.length; i-- > 0;) {
            if (stat[i] == FULL) {
                elements[j++] = val[i];
            }
        }
        return elements;
    }

    /**
     * Chooses a new prime table capacity optimized for growing that
     * (approximately) satisfies the invariant
     * <tt>c * minLoadFactor <= size <= c * maxLoadFactor</tt>
     * and has at least one FREE slot for the given size.
     */
    private int chooseGrowCapacity(int size, double minLoad, double maxLoad) {
        return nextPrime(Math.max(size + 1, (int)((4 * size / (3 * minLoad + maxLoad)))));
    }

    /**
     * Returns new high water mark threshold based on current capacity and
     * maxLoadFactor.
     *
     * @return int the new threshold.
     */
    private int chooseHighWaterMark(int capacity, double maxLoad) {
        //makes sure there is always at least one FREE slot
        return Math.min(capacity - 2, (int)(capacity * maxLoad));
    }

    /**
     * Returns new low water mark threshold based on current capacity and minLoadFactor.
     *
     * @return int the new threshold.
     */
    protected int chooseLowWaterMark(int capacity, double minLoad) {
        return (int)(capacity * minLoad);
    }

    /**
     * Chooses a new prime table capacity neither favoring shrinking nor growing,
     * that (approximately) satisfies the invariant
     * <tt>c * minLoadFactor <= size <= c * maxLoadFactor</tt>
     * and has at least one FREE slot for the given size.
     */
    protected int chooseMeanCapacity(int size, double minLoad, double maxLoad) {
        return nextPrime(Math.max(size + 1, (int)((2 * size / (minLoad + maxLoad)))));
    }

    /**
     * Chooses a new prime table capacity optimized for shrinking that
     * (approximately) satisfies the invariant
     * <tt>c * minLoadFactor <= size <= c * maxLoadFactor</tt>
     * and has at least one FREE slot for the given size.
     */
    protected int chooseShrinkCapacity(int size, double minLoad, double maxLoad) {
        return nextPrime(Math.max(size + 1, (int)((4 * size / (minLoad + 3 * maxLoad)))));
    }

    /**
     * Returns a prime number which is <code>&gt;= desiredCapacity</code> and
     * very close to <code>desiredCapacity</code> (within 11% if
     * <code>desiredCapacity &gt;= 1000</code>).
     *
     * @param desiredCapacity the capacity desired by the user.
     * @return the capacity which should be used for a hashtable.
     */
    protected int nextPrime(int desiredCapacity) {
        int i = java.util.Arrays.binarySearch(primeCapacities, desiredCapacity);
        if (i < 0) {
            //desired capacity not found, choose next prime greater than desired
            //capacity
            i = -i - 1; // remember the semantics of binarySearch...
        }
        return primeCapacities[i];
    }

    /**
     * The largest prime this class can generate; currently equal to <tt>Integer.MAX_VALUE</tt>.
     */
    public static final int LARGEST_PRIME = Integer.MAX_VALUE; //yes, it is prime.

    /**
     * The prime number list consists of 11 chunks. Each chunk contains prime
     * numbers. A chunk starts with a prime P1. The next element is a prime P2.
     * P2 is the smallest prime for which holds: P2 >= 2*P1. The next element
     * is P3, for which the same holds with respect to P2, and so on.<p>
     * <p/>
     * Chunks are chosen such that for any desired capacity >= 1000 the list
     * includes a prime number <= desired capacity * 1.11.* Therefore, primes
     * can be retrieved which are quite close to any desired capacity, which in
     * turn avoids wasting memory. For example, the list includes 1039,1117,
     * 1201,1277,1361,1439,1523,1597,1759,1907,2081. So if you need a
     * prime >= 1040, you will find a prime <= 1040*1.11=1154.<p>
     * <p/>
     * Chunks are chosen such that they are optimized for a hashtable
     * growthfactor of 2.0; If your hashtable has such a growthfactor then,
     * after initially "rounding to a prime" upon hashtable construction,
     * it will later expand to prime capacities such that there exist no better
     * primes.<p>
     * <p/>
     * In total these are about 32*10=320 numbers -> 1 KB of static memory
     * needed. If you are stingy, then delete every second or fourth chunk.
     */
    private static final int[] primeCapacities = {
        //chunk #0
        LARGEST_PRIME,

        //chunk #1
        5, 11, 23, 47, 97, 197, 397, 797, 1597, 3203, 6421, 12853, 25717, 51437, 102877, 205759,
        411527, 823117, 1646237, 3292489, 6584983, 13169977, 26339969, 52679969, 105359939,
        210719881, 421439783, 842879579, 1685759167,

        //chunk #2
        433, 877, 1759, 3527, 7057, 14143, 28289, 56591, 113189, 226379, 452759, 905551, 1811107,
        3622219, 7244441, 14488931, 28977863, 57955739, 115911563, 231823147, 463646329, 927292699,
        1854585413,

        //chunk #3
        953, 1907, 3821, 7643, 15287, 30577, 61169, 122347, 244703, 489407, 978821, 1957651, 3915341,
        7830701, 15661423, 31322867, 62645741, 125291483, 250582987, 501165979, 1002331963,
        2004663929,

        //chunk #4
        1039, 2081, 4177, 8363, 16729, 33461, 66923, 133853, 267713, 535481, 1070981, 2141977, 4283963,
        8567929, 17135863, 34271747, 68543509, 137087021, 274174111, 548348231, 1096696463,

        //chunk #5
        31, 67, 137, 277, 557, 1117, 2237, 4481, 8963, 17929, 35863, 71741, 143483, 286973, 573953,
        1147921, 2295859, 4591721, 9183457, 18366923, 36733847, 73467739, 146935499, 293871013,
        587742049, 1175484103,

        //chunk #6
        599, 1201, 2411, 4831, 9677, 19373, 38747, 77509, 155027, 310081, 620171, 1240361, 2480729,
        4961459, 9922933, 19845871, 39691759, 79383533, 158767069, 317534141, 635068283, 1270136683,

        //chunk #7
        311, 631, 1277, 2557, 5119, 10243, 20507, 41017, 82037, 164089, 328213, 656429, 1312867,
        2625761, 5251529, 10503061, 21006137, 42012281, 84024581, 168049163, 336098327, 672196673,
        1344393353,

        //chunk #8
        3, 7, 17, 37, 79, 163, 331, 673, 1361, 2729, 5471, 10949, 21911, 43853, 87719, 175447, 350899,
        701819, 1403641, 2807303, 5614657, 11229331, 22458671, 44917381, 89834777, 179669557,
        359339171, 718678369, 1437356741,

        //chunk #9
        43, 89, 179, 359, 719, 1439, 2879, 5779, 11579, 23159, 46327, 92657, 185323, 370661, 741337,
        1482707, 2965421, 5930887, 11861791, 23723597, 47447201, 94894427, 189788857, 379577741,
        759155483, 1518310967,

        //chunk #10
        379, 761, 1523, 3049, 6101, 12203, 24407, 48817, 97649, 195311, 390647, 781301, 1562611,
        3125257, 6250537, 12501169, 25002389, 50004791, 100009607, 200019221, 400038451, 800076929,
        1600153859
    };

    static { //initializer
        // The above prime numbers are formatted for human readability.
        // To find numbers fast, we sort them once and for all.
        java.util.Arrays.sort(primeCapacities);
    }
}