/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.resultsetmanager;

import java.util.*;

/**
 * A result set representation as described in XEP-0059. Note that this result
 * 'set' actually makes use of a List implementations, as the Java Set
 * definition disallows duplicate elements, while the List definition supplies
 * most of the required indexing operations.
 * 
 * This ResultSet implementation loads all all results from the set into memory,
 * which might be undesirable for very large sets, or for sets where the
 * retrieval of a result is an expensive operation. sets.
 * 
 * As most methods are backed by the {@link List#subList(int, int)} method,
 * non-structural changes in the returned lists are reflected in the ResultSet,
 * and vice-versa.
 * 
 * @author Guus der Kinderen, guus@nimbuzz.com
 * 
 * @param <E>
 *            Each result set should be a collection of instances of the exact
 *            same class. This class must implement the {@link Result}
 *            interface.
 * @see java.util.List#subList(int, int)
 * 
 */
/*
 * TODO: do we want changes to the returned Lists of methods in this class be
 * applied to the content of the ResultSet itself? Currently, because of the
 * usage of java.util.List#subList(int, int), it does. I'm thinking a
 * immodifiable solution would cause less problems. -Guus
 */
public class ResultSetImpl<E extends Result> extends ResultSet<E> {

	/**
	 * A list of all results in this ResultSet
	 */
	public final List<E> resultList;

	/**
	 * A mapping of the UIDs of all results in resultList, to the index of those
	 * entries in that list.
	 */
	public final Map<String, Integer> uidToIndex;

	/**
	 * Creates a new Result Set instance, based on a collection of Result
	 * implementing objects. The collection should contain elements of the exact
	 * same class only, and cannot contain 'null' elements.
	 * 
	 * The order that's being used in the new ResultSet instance is the same
	 * order in which {@link Collection#iterator()} iterates over the
	 * collection.
	 * 
	 * Note that this constructor throws an IllegalArgumentException if the
	 * Collection that is provided contains Results that have duplicate UIDs.
	 * 
	 * @param results
	 *            The collection of Results that make up this result set.
	 */
	public ResultSetImpl(Collection<E> results) {
		this(results, null);
	}

	/**
	 * Creates a new Result Set instance, based on a collection of Result
	 * implementing objects. The collection should contain elements of the exact
	 * same class only, and cannot contain 'null' elements.
	 * 
	 * The order that's being used in the new ResultSet instance is defined by
	 * the supplied Comparator class.
	 * 
	 * Note that this constructor throws an IllegalArgumentException if the
	 * Collection that is provided contains Results that have duplicate UIDs.
	 * 
	 * @param results
	 *            The collection of Results that make up this result set.
	 * @param comparator
	 *            The Comparator that defines the order of the Results in this
	 *            result set.
	 */
	public ResultSetImpl(Collection<E> results, Comparator<E> comparator) {
		if (results == null) {
			throw new NullPointerException("Argument 'results' cannot be null.");
		}

		final int size = results.size();
		resultList = new ArrayList<E>(size);
		uidToIndex = new Hashtable<String, Integer>(size);

		// sort the collection, if need be.
		List<E> sortedResults = null;
		if (comparator != null) {
			sortedResults = new ArrayList<E>(results);
			Collections.sort(sortedResults, comparator);
		}

		int index = 0;
		// iterate over either the sorted or unsorted collection
		for (final E result : (sortedResults != null ? sortedResults : results)) {
			if (result == null) {
				throw new NullPointerException(
						"The result set must not contain 'null' elements.");
			}

			final String uid = result.getUID();
			if (uidToIndex.containsKey(uid)) {
				throw new IllegalArgumentException(
						"The result set can not contain elements that have the same UID.");
			}

			resultList.add(result);
			uidToIndex.put(uid, index);
			index++;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.buzzaa.xmpp.resultsetmanager.ResultSet#size()
	 */
	@Override
	public int size() {
		return resultList.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.buzzaa.xmpp.resultsetmanager.ResultSet#getAfter(E, int)
	 */
	@Override
	public List<E> getAfter(String uid, int maxAmount) {
		if (uid == null || uid.length() == 0) {
			throw new NullPointerException("Argument 'uid' cannot be null or an empty String.");
		}

		if (maxAmount < 1) {
			throw new IllegalArgumentException(
					"Argument 'maxAmount' must be a integer higher than zero.");
		}

		// the result of this method is exclusive 'result'
		final int index = uidToIndex.get(uid) + 1;

		return get(index, maxAmount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.buzzaa.xmpp.resultsetmanager.ResultSet#getBefore(E, int)
	 */
	@Override
	public List<E> getBefore(String uid, int maxAmount) {
		if (uid == null || uid.length() == 0) {
			throw new NullPointerException("Argument 'uid' cannot be null or an empty String.");
		}

		if (maxAmount < 1) {
			throw new IllegalArgumentException(
					"Argument 'maxAmount' must be a integer higher than zero.");
		}

		// the result of this method is exclusive 'result'
		final int indexOfLastElement = uidToIndex.get(uid);
		final int indexOfFirstElement = indexOfLastElement - maxAmount;

		if (indexOfFirstElement < 0) {
			return get(0, indexOfLastElement);
		}

		return get(indexOfFirstElement, maxAmount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.buzzaa.xmpp.resultsetmanager.ResultSet#get(int)
	 */
	@Override
	public E get(int index) {
		return resultList.get(index);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.buzzaa.xmpp.resultsetmanager.ResultSet#getFirst(int)
	 */
	@Override
	public List<E> getFirst(int maxAmount) {
		if (maxAmount < 1) {
			throw new IllegalArgumentException(
					"Argument 'maxAmount' must be a integer higher than zero.");
		}

		return get(0, maxAmount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.buzzaa.xmpp.resultsetmanager.ResultSet#getLast(int)
	 */
	@Override
	public List<E> getLast(int maxAmount) {
		if (maxAmount < 1) {
			throw new IllegalArgumentException(
					"Argument 'maxAmount' must be a integer higher than zero.");
		}

		final int indexOfFirstElement = size() - maxAmount;

		if (indexOfFirstElement < 0) {
			return get(0, maxAmount);
		}

		return get(indexOfFirstElement, maxAmount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.buzzaa.xmpp.resultsetmanager.ResultSet#get(int, int)
	 */
	@Override
	public List<E> get(int fromIndex, int maxAmount) {
		if (fromIndex < 0) {
			throw new IllegalArgumentException(
					"Argument 'fromIndex' must be zero or higher.");
		}

		if (maxAmount < 1) {
			throw new IllegalArgumentException(
					"Argument 'maxAmount' must be a integer higher than zero.");
		}

		if (fromIndex >= size()) {
			return new ArrayList<E>(0);
		}

		// calculate the last index to return, or return up to the end of last
		// index if 'amount' surpasses the list length.
		final int absoluteTo = fromIndex + maxAmount;
		final int toIndex = (absoluteTo > size() ? size() : absoluteTo);

		return resultList.subList(fromIndex, toIndex);
	}

	/*
	 * (non-Javadoc)
	 * @see org.jivesoftware.util.resultsetmanager.ResultSet#indexOf(java.lang.String)
	 */
	@Override
	public int indexOf(String uid) {
		return uidToIndex.get(uid);
	}
}
