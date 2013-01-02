package org.jivesoftware.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Set;

public class Immutable {
	
	/**
	 * Wraps an existing {@link Collection} to provide read-only access to its contents.
	 */
	public static class Collection<V> extends java.util.AbstractCollection<V> {

		private java.util.Collection<V> delegate;
		
		public Collection(java.util.Collection<V> delegate) {
			this.delegate = delegate;
		}

		@Override
		public Iterator<V> iterator() {
			return new Iterator<V>(delegate.iterator());
		}

		@Override
		public int size() {
			return delegate.size();
		}
	}

	/**
	 * Read-only {@link Iterator} prevents removal of objects
	 */
	public static class Iterator<V> implements java.util.Iterator<V> {

		private java.util.Iterator<V> delegate;
		
		public Iterator(java.util.Iterator<V> delegate) {
			this.delegate = delegate;
		}
		public boolean hasNext() {
			return delegate.hasNext();
		}

		public V next() {
			return delegate.next();
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Wraps a {@link Map} to provide read-only access to its elements.
	 */
	public static class Map<K,V> extends AbstractMap<K,V> {

		private java.util.Map<K,V> backingMap;
		
		/**
		 * Use this constructor to provide a pre-populated map that will be
		 * made read-only via this wrapper class
		 * @param backingMap
		 */
		public Map(java.util.Map<K,V> backingMap) {
			this.backingMap = backingMap;
		}
		/**
		 * Default constructor (empty map)
		 */
		public Map() { }

		@Override
		public Set<Map.Entry<K,V>> entrySet() {
			if (backingMap == null) {
				return Collections.emptySet();
			} else {
				return new AbstractSet<Map.Entry<K,V>>() {
					public Iterator<Map.Entry<K,V>> iterator() {
						return new Iterator<Entry<K, V>>(backingMap.entrySet().iterator());
					}
					public int size() {
						return backingMap.size();
					}
				};
			}
		}
	}
	
}
