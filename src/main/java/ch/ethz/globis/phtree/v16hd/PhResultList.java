/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable. All rights reserved.
 *
 * This file is part of the PH-Tree project.
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
package ch.ethz.globis.phtree.v16hd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.util.PhMapper;

/**
 * Immutable result list.
 * 
 * This should act as a result buffer and should be used as follows.
 * API-Users simply use it as an immutable List.
 * 
 * Internally, it should try to reuse instances as much as possible. 
 * 
 * 
 * @author ztilmann
 *
 * @param <T> Value type of the Phtree
 * @param <R> Result type, such as PhEntry
 */
public abstract class PhResultList<T, R> implements List<R> {
	
	@FunctionalInterface
	interface PhEntryFactory<T> {
		/**
		 * @return A new PhEntry instance
		 */
		PhEntry<T> create();
	}
	
	@FunctionalInterface
	public interface PhResultMapper<T, R> {
		static <T> PhResultMapper<T, PhEntry<T>> NO_MAP() {
			return e -> e;
		}
		R map(PhEntry<T> e);
	}
	
	/**
	 * This method should be used to get PhEntry instances that can be offered to
	 * {@link #phOffer(PhEntry)}.
	 * 
	 * Every temp entry must be returned via {@link #phReturnTemp(PhEntry)} or
	 * {@link #phOffer(PhEntry)} before the next temp entry is available.
	 * This ensures that a temp entry is only used in one place at a time.
	 * 
	 * @return a PhEntry instance that can be used. 
	 */
	abstract PhEntry<T> phGetTempEntry();
	
	/**
	 * Return a temporary entry.
	 * @param entry entry
	 * @see PhResultList#phGetTempEntry()
	 */
	abstract void phReturnTemp(PhEntry<T> entry);

	/**
	 * Offer a PhEntry to the list. The PhEntry may be stored inside the List and
	 * should not be used anymore.
	 * This method checks the offered entry and adds it only if it passes the filter
	 * of this List.
	 *  
	 * @param e entry
	 */
	abstract void phOffer(PhEntry<T> e);
	
	abstract boolean phIsPrefixValid(long[] prefix, int bitsToIgnore);
	
	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public boolean contains(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<R> iterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <U> U[] toArray(U[] a) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean add(R e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o: c) {
			if (!contains(o)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends R> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, Collection<? extends R> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public R set(int index, R element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, R element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public R remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int lastIndexOf(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ListIterator<R> listIterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ListIterator<R> listIterator(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<R> subList(int fromIndex, int toIndex) {
		throw new UnsupportedOperationException();
	}
	
	static class SimpleArrayResultList<T> extends PhResultList<T, PhEntry<T>> {

		private final ArrayList<PhEntry<T>> list;
		private PhEntry<T> free;
		private final PhFilter filter;
		
		public SimpleArrayResultList(int dims, PhFilter filter) {
			this.list = new ArrayList<>();
			this.free = new PhEntry<>(new long[dims], null);
			this.filter = filter;
		}
		
		@Override
		public int size() {
			return list.size();
		}

		@Override
		public void clear() {
			list.clear();
		}

		@Override
		public PhEntry<T> get(int index) {
			return list.get(index);
		}

		@Override
		PhEntry<T> phGetTempEntry() {
			PhEntry<T> ret = free;
			free = null;
			return ret;
		}

		@Override
		void phReturnTemp(PhEntry<T> entry) {
			if (free == null) {
				free = entry;
			}
		}

		@Override
		void phOffer(PhEntry<T> e) {
			if (filter == null || filter.isValid(e.getKey())) {
				list.add(e);
				free = new PhEntry<>(new long[e.getKey().length], null);
			} else {
				free = e;
			}
		}

		@Override
		boolean phIsPrefixValid(long[] prefix, int bitsToIgnore) {
			return filter == null || filter.isValid(bitsToIgnore, prefix);
		}
	}
	
	static class MappingResultList<T, R> extends PhResultList<T, R> {

		private final ArrayList<R> list;
		private PhEntry<T> free;
		private final PhFilter filter;
		private final PhMapper<T, R> mapper;
		private final PhEntryFactory<T> factory;
		
		MappingResultList(PhFilter filter, PhMapper<T, R> mapper,
				PhEntryFactory<T> factory) {
			this.list = new ArrayList<>();
			this.free = factory.create();
			this.filter = filter;
			this.mapper = mapper;
			this.factory = factory;
		}
		
		@Override
		public int size() {
			return list.size();
		}

		@Override
		public void clear() {
			list.clear();
		}

		@Override
		public R get(int index) {
			return list.get(index);
		}

		@Override
		PhEntry<T> phGetTempEntry() {
			PhEntry<T> ret = free;
			free = null;
			return ret;
		}

		@Override
		void phReturnTemp(PhEntry<T> entry) {
			if (free == null) {
				free = entry;
			}
		}

		@Override
		void phOffer(PhEntry<T> e) {
			if (filter == null || filter.isValid(e.getKey())) {
				R e2 = mapper.map(e);
				list.add(e2);
				//resuse e unless e is added to the list.
				free = e == e2 ? factory.create() : e;
			} else {
				free = e;
			}
		}

		@Override
		boolean phIsPrefixValid(long[] prefix, int bitsToIgnore) {
			return filter == null || filter.isValid(bitsToIgnore, prefix);
		}
		
		@Override
		public Iterator<R> iterator() {
			return Collections.unmodifiableList(list).iterator();
		}
	}
		
}