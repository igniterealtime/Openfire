/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2001 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.util;

/**
 * A List type class for long values. The implementation uses an array. If the number
 * of elements grows larger than the capacity, the capacity will automatically grow.
 *
 * @author Matt Tucker
 */
public final class LongList {

    long[] elements;
    int capacity;
    int size;

    /**
     * Creates a new list of long values with a default capacity of 50.
     */
    public LongList() {
        this(50);
    }

    /**
     * Creates a new list of long values with a specified initial capacity.
     *
     * @param initialCapacity a capacity to initialize the list with.
     */
    public LongList(int initialCapacity) {
        size = 0;
        capacity = initialCapacity;
        elements = new long[capacity];
    }

    /**
     * Creates a new list of long values with an initial array of elements.
     *
     * @param longArray an array to create a list from.
     */
    public LongList(long[] longArray) {
        size = longArray.length;
        capacity = longArray.length + 3;
        elements = new long[capacity];
        System.arraycopy(longArray, 0, elements, 0, size);
    }

    /**
     * Adds a long value to the end of the list.
     *
     * @param value the value to add to the list.
     */
    public void add(long value) {
        elements[size] = value;
        size++;
        // Expand elements array if we need to.
        if (size == capacity) {
            capacity = capacity * 2;
            long[] newElements = new long[capacity];
            for (int i = 0; i < size; i++) {
                newElements[i] = elements[i];
            }
            elements = newElements;
        }
    }

    /**
     * Adds a long value to the list at the specified index.
     *
     * @param index the index in the list to add the value at.
     * @param value the value to add to the list.
     */
    public void add(int index, long value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index " + index + " not valid.");
        }
        // Shift elements starting at the index forward.
        for (int i = size; i > index; i--) {
            elements[i] = elements[i - 1];
        }
        elements[index] = value;
        size++;

        // Expand elements array if we need to.
        if (size == capacity) {
            capacity = capacity * 2;
            long[] newElements = new long[capacity];
            for (int i = 0; i < size; i++) {
                newElements[i] = elements[i];
            }
            elements = newElements;
        }
    }

    /**
     * Removes a value from the list at the specified index.
     *
     * @param index the index to remove a value at.
     */
    public void remove(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " not valid.");
        }
        size--;
        // Shift elements starting at the index backwards.
        for (int i = index; i < size; i++) {
            elements[i] = elements[i + 1];
        }
    }

    /**
     * Returns the long value at the specified index. If the index is not
     * valid, an IndexOutOfBoundException will be thrown.
     *
     * @param index the index of the value to return.
     * @return the value at the specified index.
     */
    public long get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " not valid.");
        }
        return elements[index];
    }

    /**
     * Returns the index in this list of the first occurrence of the specified value,
     * or -1 if this list does not contain this value.
     *
     * @param value the value to search for.
     * @return the index in this list of the first occurrence of the specified
     *         value, or -1 if this list does not contain this value.
     */
    public int indexOf(long value) {
        for (int i = 0; i < size; i++) {
            if (elements[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns true if the list contains the specified value.
     *
     * @param value the value to search for.
     * @return true if <tt>value</tt> is found in the list.
     */
    public boolean contains(long value) {
        return indexOf(value) != -1;
    }

    /**
     * Returns the number of elements in the list.
     *
     * @return the number of elements in the list.
     */
    public int size() {
        return size;
    }

    /**
     * Returns a new array containing the list elements.
     *
     * @return an array of the list elements.
     */
    public long[] toArray() {
        int size = this.size;
        long[] newElements = new long[size];
        for (int i = 0; i < size; i++) {
            newElements[i] = elements[i];
        }
        return newElements;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < this.size; i++) {
            buf.append(elements[i]).append(" ");
        }
        return buf.toString();
    }
}