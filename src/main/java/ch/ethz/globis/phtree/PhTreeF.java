/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
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
package ch.ethz.globis.phtree;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import ch.ethz.globis.phtree.PhTree.PhExtent;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.PhTree.PhQuery;
import ch.ethz.globis.phtree.pre.PreProcessorPointF;
import ch.ethz.globis.phtree.util.PhIteratorBase;
import ch.ethz.globis.phtree.util.PhMapper;
import ch.ethz.globis.phtree.util.PhMapperK;
import ch.ethz.globis.phtree.util.PhTreeStats;

/**
 * k-dimensional index (quad-/oct-/n-tree).
 * Supports key/value pairs.
 *
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 * @param <T> The value type of the tree 
 *
 */
public class PhTreeF<T> {

	private final PhTree<T> pht;
	private final PreProcessorPointF pre;

	protected PhTreeF(int dim, PreProcessorPointF pre) {
		this.pht = PhTree.create(dim);
		this.pre = pre;
	}

	protected PhTreeF(PhTree<T> tree) {
		this.pht = tree;
		this.pre = new PreProcessorPointF.IEEE();
	}

	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 * @return PhTreeF
	 * @param <T> value type of the tree
	 */
	public static <T> PhTreeF<T> create(int dim) {
		return new PhTreeF<>(dim, new PreProcessorPointF.IEEE());
	}

	/**
	 * Create a new tree with the specified number of dimensions and
	 * a custom preprocessor.
	 * 
	 * @param dim number of dimensions
	 * @param pre The preprocessor to be used
	 * @return PhTreeF
	 * @param <T> value type of the tree
	 */
	public static <T> PhTreeF<T> create(int dim, PreProcessorPointF pre) {
		return new PhTreeF<>(dim, pre);
	}

	/**
	 * Create a new PhTreeF as a wrapper around an existing PhTree.
	 * 
	 * @param tree another tree
	 * @return PhTreeF
	 * @param <T> value type of the tree
	 */
	public static <T> PhTreeF<T> wrap(PhTree<T> tree) {
		return new PhTreeF<>(tree);
	}

	/**
	 * @return the number of entries in the tree
	 */
	public int size() {
		return pht.size();
	}

	/**
	 * Insert an entry associated with a k dimensional key.
	 * @param key the key to store the value to store
	 * @param value the value
	 * @return the previously associated value or {@code null} if the key was found
	 */
	public T put(double[] key, T value) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.put(lKey, value);
	}

	/**
	 * @param key key
	 * @return true if the key exists in the tree
	 */
	public boolean contains(double ... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.contains(lKey);
	}

	/**
	 * @param key the key
	 * @return the value associated with the key or 'null' if the key was not found
	 */
	public T get(double ... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.get(lKey);
	}


	/**
	 * Remove the entry associated with a k dimensional key.
	 * @param key the key to remove
	 * @return the associated value or {@code null} if the key was found
	 */
	public T remove(double... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.remove(lKey);
	}

	/**
	 * @return an iterator over all elements in the tree
	 */
	public PhExtentF<T> queryExtent() {
		return new PhExtentF<>(pht.queryExtent(), pht.getDim(), pre);
	}


	/**
	 * Performs a rectangular window query. The parameters are the min and max keys which 
	 * contain the minimum respectively the maximum keys in every dimension.
	 * @param min Minimum values
	 * @param max Maximum values
	 * @return Result iterator.
	 */
	public PhQueryF<T> query(double[] min, double[] max) {
		long[] lMin = new long[min.length];
		long[] lMax = new long[max.length];
		pre.pre(min, lMin);
		pre.pre(max, lMax);
		return new PhQueryF<>(pht.query(lMin, lMax), pht.getDim(), pre);
	}

	/**
	 * Find all entries within a given distance from a center point.
	 * @param dist Maximum distance
	 * @param center Center point
	 * @return All entries with at most distance `dist` from `center`.
	 */
	public PhRangeQueryF<T> rangeQuery(double dist, double...center) {
		return rangeQuery(dist, PhDistanceF.THIS, center);
	}

	/**
	 * Find all entries within a given distance from a center point.
	 * @param dist Maximum distance
	 * @param optionalDist Distance function, optional, can be `null`.
	 * @param center Center point
	 * @return All entries with at most distance `dist` from `center`.
	 */
	public PhRangeQueryF<T> rangeQuery(double dist, PhDistance optionalDist, double...center) {
		if (optionalDist == null) {
			optionalDist = PhDistanceF.THIS; 
		}
		long[] lKey = new long[center.length];
		pre.pre(center, lKey);
		PhRangeQuery<T> iter = pht.rangeQuery(dist, optionalDist, lKey);
		return new PhRangeQueryF<>(iter, pht, pre);
	}

	public int getDim() {
		return pht.getDim();
	}

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param key the center point
	 * @return List of neighbours.
	 */
	public PhKnnQueryF<T> nearestNeighbour(int nMin, double... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		PhKnnQuery<T> iter = pht.nearestNeighbour(nMin, PhDistanceF.THIS, null, lKey);
		return new PhKnnQueryF<>(iter, pht.getDim(), pre);
	}

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param dist Distance function. Note that the distance function should be compatible
	 * with the preprocessor of the tree.
	 * @param key the center point
	 * @return KNN query iterator.
	 */
	public PhKnnQueryF<T> nearestNeighbour(int nMin, PhDistance dist, double... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		PhKnnQuery<T> iter = pht.nearestNeighbour(nMin, dist, null, lKey);
		return new PhKnnQueryF<>(iter, pht.getDim(), pre);
	}

	/**
	 * Iterator class for floating point keys. 
	 * @param <T> value type
	 */
	public static class PhIteratorF<T> implements PhIteratorBase<T, PhEntryF<T>> {
		private final PhIteratorBase<T, ? extends PhEntry<T>> iter;
		protected final PreProcessorPointF pre;
		private final int dims;
		private final PhEntryF<T> buffer;

		protected PhIteratorF(PhIteratorBase<T, ? extends PhEntry<T>> iter, 
				int dims, PreProcessorPointF pre) {
			this.iter = iter;
			this.pre = pre;
			this.dims = dims;
			this.buffer = new PhEntryF<>(new double[dims], null);
		}

		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public T next() {
			return nextValue();
		}

		@Override
		public PhEntryF<T> nextEntry() {
			double[] d = new double[dims];
			PhEntry<T> e = iter.nextEntryReuse();
			pre.post(e.getKey(), d);
			return new PhEntryF<>(d, e.getValue());
		}

		@Override
		public PhEntryF<T> nextEntryReuse() {
			PhEntry<T> e = iter.nextEntryReuse();
			pre.post(e.getKey(), buffer.getKey());
			buffer.setValue( e.getValue() );
			return buffer;
		}

		/**
		 * @return the key of the next entry
		 */
		public double[] nextKey() {
			double[] d = new double[dims];
			pre.post(iter.nextEntryReuse().getKey(), d);
			return d;
		}

		@Override
		public T nextValue() {
			return iter.nextValue();
		}

		@Override
		public void remove() {
			iter.remove();
		}
	}

	/**
	 * Extent iterator class for floating point keys. 
	 * @param <T> value type
	 */
	public static class PhExtentF<T> extends PhIteratorF<T> {
		private final PhExtent<T> iter;
		protected PhExtentF(PhExtent<T> iter, int dims, PreProcessorPointF pre) {
			super(iter, dims, pre);
			this.iter = iter;
		}		
		
		/**
		 * Restarts the extent iterator.
		 * @return this
		 */
		public PhExtentF<T> reset() {
			iter.reset();
			return this;
		}
	}
	
	/**
	 * Query iterator class for floating point keys. 
	 * @param <T> value type
	 */
	public static class PhQueryF<T> extends PhIteratorF<T> {
		private final long[] lMin;
		private final long[] lMax;
		private final PhQuery<T> q;

		protected PhQueryF(PhQuery<T> iter, int dims, PreProcessorPointF pre) {
			super(iter, dims, pre);
			q = iter;
			lMin = new long[dims];
			lMax = new long[dims];
		}

		/**
		 * Restarts the query with a new query rectangle.
		 * @param lower minimum values of query rectangle
		 * @param upper maximum values of query rectangle
		 */
		public void reset(double[] lower, double[] upper) {
			pre.pre(lower, lMin);
			pre.pre(upper, lMax);
			q.reset(lMin, lMax);
		}
	}

	/**
	 * Nearest neighbor query iterator class for floating point keys. 
	 * @param <T> value type
	 */
	public static class PhKnnQueryF<T> extends PhIteratorF<T> {
		private final long[] lCenter;
		private final PhKnnQuery<T> q;
		private final PhEntryDistF<T> buffer;
		private final int dims;

		protected PhKnnQueryF(PhKnnQuery<T> iter, int dims, PreProcessorPointF pre) {
			super(iter, dims, pre);
			this.dims = dims;
			q = iter;
			lCenter = new long[dims];
			buffer = new PhEntryDistF<>(new double[dims], null, Double.NaN); 
		}

		@Override
		public PhEntryDistF<T> nextEntry() {
			double[] d = new double[dims];
			PhEntryDist<T> e = q.nextEntryReuse();
			pre.post(e.getKey(), d);
			return new PhEntryDistF<>(d, e.getValue(), e.dist());
		}

		@Override
		public PhEntryDistF<T> nextEntryReuse() {
			PhEntryDist<T> e = q.nextEntryReuse();
			pre.post(e.getKey(), buffer.getKey());
			buffer.set( e.getValue(), e.dist() );
			return buffer;
		}

		/**
		 * Restarts the query with a new center point.
		 * @param nMin new minimum result count, often called 'k'
		 * @param dist new distance function. Using 'null' will result in reusing the previous
		 * distance function.
		 * @param center new center point
		 * @return this
		 */
		public PhKnnQueryF<T> reset(int nMin, PhDistance dist, double... center) {
			pre.pre(center, lCenter);
			q.reset(nMin, dist, lCenter);
			return this;
		}
	}

	/**
	 * Range query iterator class for floating point keys. 
	 * @param <T> value type
	 */
	public static class PhRangeQueryF<T> extends PhIteratorF<T> {
		private final long[] lCenter;
		private final PhRangeQuery<T> q;
		private final int dims;

		protected PhRangeQueryF(PhRangeQuery<T> iter, PhTree<T> tree, PreProcessorPointF pre) {
			super(iter, tree.getDim(), pre);
			this.dims = tree.getDim();
			this.q = iter;
			this.lCenter = new long[dims];
		}

		/**
		 * Restarts the query with a new center point and range.
		 * @param range new range
		 * @param center new center point
		 * @return this
		 */
		public PhRangeQueryF<T> reset(double range, double... center) {
			pre.pre(center, lCenter);
			q.reset(range, lCenter);
			return this;
		}
	}

	/**
	 * Entry class for Double entries.
	 *
	 * @param <T> value type of the entries
	 */
	public static class PhEntryF<T> {
		protected double[] key;
		protected T value;
		
		/**
		 * @param key the key
		 * @param value the value
		 */
		public PhEntryF(double[] key, T value) {
			this.key = key;
			this.value = value;
		}

		public double[] getKey() {
			return key;
		}

		public T getValue() {
			return value;
		}

		public void setValue(T value) {
			this.value = value;
		}
	}

	/**
	 * Entry class for Double entries with distance information for nearest neighbour queries.
	 *
	 * @param <T> value type of the entries
	 */
	public static class PhEntryDistF<T> extends PhEntryF<T> {
		private double dist;

		/**
		 * @param key the key
		 * @param value the value
		 * @param dist the distance to the center point
		 */
		public PhEntryDistF(double[] key, T value, double dist) {
			super(key, value);
			this.dist = dist;
		}

		/**
		 * @param value new value
		 * @param dist new distance
		 */
		public void set(T value, double dist) {
			this.value = value;
			this.dist = dist;
		}
		
		/**
		 * @return distance to center point of kNN query
		 */
		public double dist() {
			return dist;
		}
	}
	
	/**
	 * Update the key of an entry. Update may fail if the old key does not exist, or if the new
	 * key already exists.
	 * @param oldKey old key
	 * @param newKey new key
	 * @return the value (can be {@code null}) associated with the updated key if the key could be 
	 * updated, otherwise {@code null}.
	 */
	public T update(double[] oldKey, double[] newKey) {
		long[] oldL = new long[oldKey.length];
		long[] newL = new long[newKey.length];
		pre.pre(oldKey, oldL);
		pre.pre(newKey, newL);
		return pht.update(oldL, newL);
	}

	/**
	 * Same as {@link #query(double[], double[])}, except that it returns a list
	 * instead of an iterator. This may be faster for small result sets. 
	 * @param min min values
	 * @param max max values
	 * @return List of query results
	 */
	public List<PhEntryF<T>> queryAll(double[] min, double[] max) {
		return queryAll(min, max, Integer.MAX_VALUE, null,
				e -> new PhEntryF<>(PhMapperK.toDouble(e.getKey()), e.getValue()));
	}

	/**
	 * Same as {@link PhTreeF#queryAll(double[], double[])}, except that it also accepts
	 * a limit for the result size, a filter and a mapper. 
	 * @param min min key
	 * @param max max key
	 * @param maxResults maximum result count 
	 * @param filter filter object (optional)
	 * @param mapper mapper object (optional)
	 * @return List of query results
	 * @param <R> value type
	 */
	public <R> List<R> queryAll(double[] min, double[] max, int maxResults, 
			PhFilter filter, PhMapper<T, R> mapper) {
		long[] lUpp = new long[min.length];
		long[] lLow = new long[max.length];
		pre.pre(min, lLow);
		pre.pre(max, lUpp);
		return pht.queryAll(lLow, lUpp, maxResults, filter, mapper);
	}

	/**
	 * Clear the tree.
	 */
	public void clear() {
		pht.clear();
	}

	/**
	 * 
	 * @return the internal PhTree that backs this PhTreeF.
	 */
	public PhTree<T> getInternalTree() {
		return pht;
	}

	/**
	 * 
	 * @return the preprocessor of this tree.
	 */
	public PreProcessorPointF getPreprocessor() {
		return pre;
	}

	/**
	 * @return A string tree view of all entries in the tree.
	 * @see PhTree#toStringTree()
	 */
	public String toStringTree() {
		return pht.toStringTree();
	}

	@Override
	public String toString() {
		return pht.toString(); 
	}

	public PhTreeStats getStats() {
		return pht.getStats();
	}


	// Overrides of JDK8 Map extension methods

	/**
	 * @see java.util.Map#getOrDefault(Object, Object)
	 * @param key key
	 * @param defaultValue default value
	 * @return actual value or default value
	 */
	public T getOrDefault(double[] key, T defaultValue) {
		T t = get(key);
		return t == null ? defaultValue : t;
	}

	/**
	 * @see java.util.Map#putIfAbsent(Object, Object)
	 * @param key key
	 * @param value new value
	 * @return previous value or null
	 */
	public T putIfAbsent(double[] key, T value) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.putIfAbsent(lKey, value);
	}

	/**
	 * @see java.util.Map#remove(Object, Object)
	 * @param key key
	 * @param value value
	 * @return {@code true} if the value was removed
	 */
	public boolean remove(double[] key, T value) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.remove(lKey, value);
	}

	/**
	 * @see java.util.Map#replace(Object, Object, Object)
	 * @param key key
	 * @param oldValue old value
	 * @param newValue new value
	 * @return {@code true} if the value was replaced
	 */
	public boolean replace(double[] key, T oldValue, T newValue) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.replace(lKey, oldValue, newValue);
	}

	/**
	 * @see java.util.Map#replace(Object, Object)
	 * @param key key
	 * @param value new value
	 * @return previous value or null
	 */
	public T replace(double[] key, T value) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.replace(lKey, value);
	}

	/**
	 * @see java.util.Map#computeIfAbsent(Object, Function)
	 * @param key key
	 * @param mappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	public T computeIfAbsent(double[] key, Function<double[], ? extends T> mappingFunction) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.computeIfAbsent(lKey, longs -> mappingFunction.apply(key));
	}

	/**
	 * @see java.util.Map#computeIfPresent(Object, BiFunction)
	 * @param key key
	 * @param remappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	public T computeIfPresent(double[] key, BiFunction<double[], ? super T, ? extends T> remappingFunction) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.computeIfPresent(lKey, (longs, t) -> remappingFunction.apply(key, t));
	}

	/**
	 * @see java.util.Map#compute(Object, BiFunction)
	 * @param key key
	 * @param remappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	public T compute(double[] key, BiFunction<double[], ? super T, ? extends T> remappingFunction) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.compute(lKey, (longs, t) -> remappingFunction.apply(key, t));
	}
}

