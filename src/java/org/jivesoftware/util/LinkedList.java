/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
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


/**
 * Simple LinkedList implementation. The main feature is that list nodes
 * are public, which allows very fast delete operations when one has a
 * reference to the node that is to be deleted.<p>
 *
 * @author Jive Software
 * @param <E>
 */
public class LinkedList<E> {

    /**
     * The root of the list keeps a reference to both the first and last
     * elements of the list.
     */
    private LinkedListNode<E> head;

    /**
     * Creates a new linked list.
     */
    public LinkedList() {
    	head = new LinkedListNode<E>();
    }

    /**
     * Returns the first linked list node in the list.
     *
     * @return the first element of the list.
     */
    public LinkedListNode<E> getFirst() {
        LinkedListNode<E> node = head.next;
        if (node == head) {
            return null;
        }
        return node;
    }

    /**
     * Returns the last linked list node in the list.
     *
     * @return the last element of the list.
     */
    public LinkedListNode<E> getLast() {
        LinkedListNode<E> node = head.previous;
        if (node == head) {
            return null;
        }
        return node;
    }

    /**
     * Adds a node to the beginning of the list.
     *
     * @param node the node to add to the beginning of the list.
     */
    public LinkedListNode<E> addFirst(LinkedListNode<E> node) {
    	return node.insert(head.next, head);
    }

    /**
     * Adds an object to the beginning of the list by automatically creating a
     * a new node and adding it to the beginning of the list.
     *
     * @param object the object to add to the beginning of the list.
     * @return the node created to wrap the object.
     */
    public LinkedListNode<E> addFirst(E object) {
        return new LinkedListNode<E>(object, head.next, head);
    }

    /**
     * Adds a node to the end of the list.
     *
     * @param node the node to add to the beginning of the list.
     */
    public LinkedListNode<E> addLast(LinkedListNode<E> node) {
    	return node.insert(head, head.previous);
    }

    /**
     * Adds an object to the end of the list by automatically creating a
     * a new node and adding it to the end of the list.
     *
     * @param object the object to add to the end of the list.
     * @return the node created to wrap the object.
     */
    public LinkedListNode<E> addLast(E object) {
        return new LinkedListNode<E>(object, head, head.previous);
    }

    /**
     * Erases all elements in the list and re-initializes it.
     */
    public void clear() {
        //Remove all references in the list.
        LinkedListNode<E> node = getLast();
        while (node != null) {
            node.remove();
            node = getLast();
        }

        //Re-initialize.
        head.next = head.previous = head;
    }

    /**
     * Returns a String representation of the linked list with a comma
     * delimited list of all the elements in the list.
     *
     * @return a String representation of the LinkedList.
     */
    @Override
	public String toString() {
        LinkedListNode<E> node = head.next;
        StringBuilder buf = new StringBuilder();
        while (node != head) {
            buf.append(node.toString()).append(", ");
            node = node.next;
        }
        return buf.toString();
    }
}
