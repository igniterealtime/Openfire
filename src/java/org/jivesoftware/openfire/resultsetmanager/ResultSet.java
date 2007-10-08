/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.resultsetmanager;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

import java.util.*;

/**
 * A result set representation as described in XEP-0059. A result set is a
 * collection of objects that each have a unique identifier (UID).
 * 
 * It's expected that some implementations will have the complete result set
 * loaded into memory, whereas more complex implementations might keep
 * references to partial sets only. This latter would have considerable
 * advantages if the result set is extremely large, or if the operation to get
 * all results in the set is expensive.
 * 
 * @author Guus der Kinderen, guus@nimbuzz.com
 * 
 * @param <E>
 *            Each result set should be a collection of instances of the exact
 *            same class. This class must implement the {@link Result}
 *            interface.
 */
public abstract class ResultSet<E extends Result> extends AbstractCollection<E> {

	/**
	 * A list of field names that are valid in jabber:iq:search
	 */
	private final static Collection<String> validRequestFields = new ArrayList<String>();
	static {
		validRequestFields.add("max"); // required
		validRequestFields.add("before");
		validRequestFields.add("after");
		validRequestFields.add("index");
	}

	/**
	 * The namespace that identifies Result Set Management functionality.
	 */
	public static final String NAMESPACE_RESULT_SET_MANAGEMENT = "http://jabber.org/protocol/rsm";

	/**
	 * Returns a List of results starting with the first result after the
	 * provided result (the returned List is exclusive).
	 * 
	 * The lenght of the list is equal to 'maxAmount', unless there are no more
	 * elements available (in which case the length of the result will be
	 * truncated).
	 * 
	 * @param result
	 *            The element that is right before the first element in the
	 *            result.
	 * @param maxAmount
	 *            The maximum number of elements to return.
	 * @return A List of elements the are exactly after the element that is
	 *         provided as a parameter.
	 * @throws NullPointerException
	 *             if the result does not exist in the result set.
	 */
	public List<E> getAfter(E result, int maxAmount) {
		return getAfter(result.getUID(), maxAmount);
	}

	/**
	 * Returns a List of results starting with the first result after the result
	 * that's identified by the provided UID (the returned List is exclusive).
	 * 
	 * The lenght of the list is equal to 'maxAmount', unless there are no more
	 * elements available (in which case the length of the result will be
	 * truncated).
	 * 
	 * @param uid
	 *            The UID of the element that is right before the first element
	 *            in the result.
	 * @param maxAmount
	 *            The maximum number of elements to return.
	 * @return A List of elements the are exactly after the element that is
	 *         provided as a parameter.
	 * @throws NullPointerException
	 *             if there is no result in the result set that matches the UID.
	 */
	public abstract List<E> getAfter(String uid, int maxAmount);

	/**
	 * Returns a list of results ending with the element right before the
	 * provided result (the returned List is exclusive).
	 * 
	 * At most 'maxAmount' elements are in the returned List, but the lenght of
	 * the result might be smaller if no more applicable elements are available.
	 * 
	 * Note that the order of the result is equal to the order of the results of
	 * other methods of this class: the last element in the returned List
	 * immediately preceeds the element denoted by the 'result' parameter.
	 * 
	 * @param result
	 *            The element preceding the last element returned by this
	 *            function.
	 * @param maxAmount
	 *            The length of the List that is being returned.
	 * @return A List of elements that are exactly before the element that's
	 *         provided as a parameter.
	 * @throws NullPointerException
	 *             if the result does not exist in the result set.
	 * 
	 */
	public List<E> getBefore(E result, int maxAmount) {
		return getBefore(result.getUID(), maxAmount);
	}

	/**
	 * Returns a list of results ending with the element right before the
	 * element identified by the provided UID (the returned List is exclusive).
	 * 
	 * At most 'maxAmount' elements are in the returned List, but the lenght of
	 * the result might be smaller if no more applicable elements are available.
	 * 
	 * Note that the order of the result is equal to the order of the results of
	 * other methods of this class: the last element in the returned List
	 * immediately preceeds the element denoted by the 'result' parameter.
	 * 
	 * @param uid
	 *            The UID of the element preceding the last element returned by
	 *            this function.
	 * @param maxAmount
	 *            The length of the List that is being returned.
	 * @return A List of elements that are exactly before the element that's
	 *         provided as a parameter.
	 * @throws NullPointerException
	 *             if there is no result in the result set that matches the UID.
	 */
	public abstract List<E> getBefore(String uid, int maxAmount);

	/**
	 * Returns the first elements from this result set.
	 * 
	 * @param maxAmount
	 *            the number of elements to return.
	 * @return the last 'maxAmount' elements of this result set.
	 */
	public abstract List<E> getFirst(int maxAmount);

	/**
	 * Returns the last elements from this result set.
	 * 
	 * @param maxAmount
	 *            the number of elements to return.
	 * @return the last 'maxAmount' elements of this result set.
	 */
	public abstract List<E> getLast(int maxAmount);

	/**
	 * Returns the element denoted by the index.
	 * 
	 * @param index
	 *            Index of the element to be returned
	 * @return the Element at 'index'.
	 */
	public abstract E get(int index);

	/**
	 * Returns a list of results, starting with the result that's at the
	 * specified index. If the difference between the startIndex and the index
	 * of the last element in the entire resultset is smaller than the size
	 * supplied in the 'amount' parameter, the length of the returned list will
	 * be smaller than the 'amount' paramater. If the supplied index is equal
	 * to, or larger than the size of the result set, an empty List is returned.
	 * 
	 * @param fromIndex
	 *            The index of the first element to be returned.
	 * @param maxAmount
	 *            The maximum number of elements to return.
	 * @return A list of elements starting with (inclusive) the element
	 *         referenced by 'fromIndex'. An empty List if startIndex is equal
	 *         to or bigger than the size of this entire result set.
	 */
	public abstract List<E> get(int fromIndex, int maxAmount);

	/**
	 * Returns the UID of the object at the specified index in this result set.
	 * 
	 * @param index
	 *            The index of the UID to be returned.
	 * @return UID of the object on the specified index.
	 */
	public String getUID(int index) {
		return get(index).getUID();
	}

	/**
	 * Returns the index in the full resultset of the element identified by the
	 * UID in te supplied argument.
	 * 
	 * @param uid
	 *            The UID of the element to search for
	 * @return The index of the element.
	 * @throws NullPointerException
	 *             if there is no result in the result set that matches the UID.
	 * 
	 */
	public abstract int indexOf(String uid);

	/**
	 * Returns the index in the full resultset of the supplied argument.
	 * 
	 * @param element
	 *            The element to search for
	 * @return The index of the element.
	 */
	public int indexOf(E element) {
		return indexOf(element.getUID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractCollection#iterator()
	 */
	@Override
	public Iterator<E> iterator() {
		return new Itr();
	}

	/**
	 * Applies the 'result set management' directives to this result set, and
	 * returns a list of Results that matches the directives. Note that the
	 * orignal set is untouched. Instead, a new List is returned.
	 * 
	 * @param rsmElement
	 *            The XML element that contains the 'result set management'
	 *            directives.
     * @return a list of Results that matches the directives.
	 */
	public List<E> applyRSMDirectives(Element rsmElement) {
		if (rsmElement == null || !isValidRSMRequest(rsmElement)) {
			throw new IllegalArgumentException(
					"The 'rsmElement' argument must be a valid, non-null RSM element.");
		}

		final int max = Integer.parseInt(rsmElement.element("max").getText());

		if (max == 0) {
			// this is a request for a resultset count.
			return Collections.emptyList();
		}

		// optional elements
		final Element afterElement = rsmElement.element("after");
		final Element beforeElement = rsmElement.element("before");
		final Element indexElement = rsmElement.element("index");

		// Identify the pointer object in this set. This is the object before
		// (or after) the first (respectivly last) element of the subset that
		// should be returned. If no pointer is specified, the pointer is said
		// to be before or after the first respectivly last element of the set.
		String pointerUID = null; // by default, the pointer is before the
		// first element of the set.

		// by default, the search list is forward oriented.
		boolean isForwardOriented = true;

		if (afterElement != null) {
			pointerUID = afterElement.getText();
		} else if (beforeElement != null) {
			pointerUID = beforeElement.getText();
			isForwardOriented = false;
		} else if (indexElement != null) {
			final int index = Integer.parseInt(indexElement.getText());
			if (index > 0) {
				pointerUID = getUID(index - 1);
			}
		}

		if (pointerUID != null && pointerUID.equals("")) {
			pointerUID = null;
		}

		if (isForwardOriented) {
			if (pointerUID == null) {
				return getFirst(max);
			}
			return getAfter(pointerUID, max);
		}

		if (pointerUID == null) {
			return getLast(max);
		}
		return getBefore(pointerUID, max);
	}

	/**
	 * Generates a Result Set Management 'set' element that describes the parto
	 * of the result set that was generated. You typically would use the List
	 * that was returned by {@link #applyRSMDirectives(Element)} as an argument
	 * to this method.
	 * 
	 * @param returnedResults
	 *            The subset of Results that is returned by the current query.
	 * @return An Element named 'set' that can be included in the result IQ
	 *         stanza, which returns the subset of results.
	 */
	public Element generateSetElementFromResults(List<E> returnedResults) {
		if (returnedResults == null) {
			throw new IllegalArgumentException(
					"Argument 'returnedResults' cannot be null.");
		}
		final Element setElement = DocumentHelper.createElement(QName.get(
				"set", ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT));
		// the size element contains the size of this entire result set.
		setElement.addElement("count").setText(String.valueOf(size()));

		// if the query wasn't a 'count only' query, add two more elements
		if (returnedResults.size() > 0) {
			final Element firstElement = setElement.addElement("first");
			firstElement.addText(returnedResults.get(0).getUID());
			firstElement.addAttribute("index", String
					.valueOf(indexOf(returnedResults.get(0))));

			setElement.addElement("last").addText(
					returnedResults.get(returnedResults.size() - 1).getUID());
		}

		return setElement;
	}

	/**
	 * Checks if the Element that has been passed as an argument is a valid
	 * Result Set Management element, in a request context.
	 * 
	 * @param rsmElement
	 *            The Element to check.
	 * @return ''true'' if this is a valid RSM query representation, ''false''
	 *         otherwise.
	 */
	// Dom4J doesn't do generics, sadly.
	@SuppressWarnings("unchecked")
	public static boolean isValidRSMRequest(Element rsmElement) {
		if (rsmElement == null) {
			throw new IllegalArgumentException(
					"The argument 'rsmElement' cannot be null.");
		}

		if (!rsmElement.getName().equals("set")) {
			// the name of the element must be "set".
			return false;
		}

		if (!rsmElement.getNamespaceURI().equals(
				NAMESPACE_RESULT_SET_MANAGEMENT)) {
			// incorrect namespace
			return false;
		}

		final Element maxElement = rsmElement.element("max");
		if (maxElement == null) {
			// The 'max' element in an RSM request must be available
			return false;
		}

		final String sMax = maxElement.getText();
		if (sMax == null || sMax.length() == 0) {
			// max element must contain a value.
			return false;
		}

		try {
			if (Integer.parseInt(sMax) < 0) {
				// must be a postive integer.
				return false;
			}
		} catch (NumberFormatException e) {
			// the value of 'max' must be an integer value.
			return false;
		}

		List<Element> allElements = rsmElement.elements();
		int optionalElements = 0;
		for (Element element : allElements) {
			final String name = element.getName();
			if (!validRequestFields.contains(name)) {
				// invalid element.
				return false;
			}

			if (!name.equals("max")) {
				optionalElements++;
			}

			if (optionalElements > 1) {
				// only one optional element is allowed.
				return false;
			}

			if (name.equals("index")) {
				final String value = element.getText();
				if (value == null || value.equals("")) {
					// index elements must have a numberic value.
					return false;
				}
				try {
					if (Integer.parseInt(value) < 0) {
						// index values must be positive.
						return false;
					}
				} catch (NumberFormatException e) {
					// index values must be numeric.
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Basic Iterator implementation. Forward scrolling only. Does not support
	 * modification.
	 * 
	 * @author Guus der Kinderen, guus@nimbuzz.com
	 * 
	 */
	class Itr implements Iterator<E> {
		/**
		 * Index of element to be returned by subsequent call to next.
		 */
		int cursor = 0;

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return cursor != size();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Iterator#next()
		 */
		public E next() {
			return get(cursor++);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}