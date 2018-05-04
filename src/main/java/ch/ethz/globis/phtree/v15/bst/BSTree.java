/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v15.bst;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;

import ch.ethz.globis.phtree.util.StringBuilderLn;


/**
 * @author Tilmann Zaeschke
 */
public class BSTree<T> {
	
	static final Object NULL = new Object();
	
	public final int maxLeafN;// = 100;//10;//340;
	/** Max number of keys in inner page (there can be max+1 page-refs) */
	public final int maxInnerN;// = 100;//11;//509;
	static int statNLeaves = 0;
	static int statNInner = 0;
	private int modCount = 0;

	private int nEntries = 0;
	
	private transient BSTreePage<T> root;
	
	public BSTree(int dims) {
		switch (dims) {
		case 1: maxLeafN = 2; maxInnerN = 2; break;
		case 2: maxLeafN = 4; maxInnerN = 2; break;
		case 3: maxLeafN = 8; maxInnerN = 2; break;
		case 4: maxLeafN = 16; maxInnerN = 2; break;
		case 5: maxLeafN = 16; maxInnerN = 2+1; break;
		case 6: maxLeafN = 16; maxInnerN = 4+1; break;
		case 7: maxLeafN = 16; maxInnerN = 8+1; break;
		case 8: maxLeafN = 16; maxInnerN = 16+1; break;
		case 9: maxLeafN = 32; maxInnerN = 16+1; break;
		case 10: maxLeafN = 32; maxInnerN = 32+1; break;
		case 11: maxLeafN = 32; maxInnerN = 64+1; break;
		case 12: maxLeafN = 64; maxInnerN = 64+1; break;
		default: maxLeafN = 100; maxInnerN = 100; break;
		}
		//bootstrap index
		root = createPage(null, false);
	}

	public BSTree(int pageSizeInner, int pageSizeLeaf) {
		this.maxInnerN = pageSizeInner;
		this.maxLeafN = pageSizeLeaf;

		//bootstrap index
		root = createPage(null, false);
	}

	public final T put(long key, T value) {
		return put(key, value, null);
	}
	
	@SuppressWarnings("unchecked")
	public final T put(long key, T value, BiFunction<T, T, Object> collisionHandler) {
		Object val = value == null ? NULL : value;
		//Depth as log(nEntries) 
		BSTreePage<T> page = getRoot();
		Object o = page;
		while (o instanceof BSTreePage && !((BSTreePage<T>)o).isLeaf()) {
			o = ((BSTreePage<T>)o).put(key, (T)val, collisionHandler, this);
		}
		if (o == null) {
			//did not exist
			nEntries++;
		}
		return (T)o;
	}

	/**
	 * @param key The key to remove
	 * @return the previous value
	 * @throws NoSuchElementException if key is not found
	 */
	public T remove(long key) {
		return remove(key, null);
	}
	
	/**
	 * @param key The key to remove
	 * @param predicateRemove Whether to remove the entry. This methods will always return T, even if
	 * the 'predicate' returns false. 
	 * @return the previous value
	 * @throws NoSuchElementException if key is not found
	 */
	@SuppressWarnings("unchecked")
	public T remove(long key, Function<T, REMOVE_OP> predicateRemove) {
		BSTreePage<T> page = getRoot();
		Object result = null;
		if (page != null) {
			result = page.findAndRemove(key, predicateRemove, this);
		}

		return result == NULL ? null : (T) result;
	}

	public LLEntry get(long key) {
		BSTreePage<T> page = getRoot();
		while (page != null && !page.isLeaf()) {
			page = page.findSubPage(key);
		}
		if (page == null) {
			return null;
		}
		return page.getValueFromLeaf(key);
	}

	BSTreePage<T> createPage(BSTreePage<T> parent, boolean isLeaf) {
		return new BSTreePage<>(this, parent, isLeaf);
	}

	BSTreePage<T> getRoot() {
		return root;
	}

	public BSTIteratorMinMax<T> iterator(long min, long max) {
		return new BSTIteratorMinMax<T>().reset(this, min, max);
	}

	public BSTIteratorMask<T> iteratorMask(long minMask, long maxMask) {
		return new BSTIteratorMask<T>().reset(this, minMask, maxMask);
	}

	void updateRoot(BSTreePage<T> newRoot) {
		root = newRoot;
	}
	
	public void print() {
		root.print("");
	}

	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		if (root != null) {
			root.toStringTree(sb, "");
		}
		return sb.toString();
	}

	public long getMaxKey() {
		return root.getMax();
	}

	public long getMinKey() {
		return root.getMinKey();
	}

	
	public BSTIteratorMinMax<T> iterator() {
		return iterator(Long.MIN_VALUE, Long.MAX_VALUE);
	}

	
	public void clear() {
		getRoot().clear();
		nEntries = 0;
		BTPool.reportFreePage(getRoot());
		BSTree.statNInner = 0;
		BSTree.statNLeaves = 0;
	}
	
	void checkValidity(int modCount) {
		if (this.modCount != modCount) {
			throw new ConcurrentModificationException();
		}
	}

	public static class Stats {
		int nNodesInner = 0;
		int nNodesLeaf = 0;
		int capacityInner = 0;
		int capacityLeaf = 0;
		int nEntriesInner = 0;
		int nEntriesLeaf = 0;
		
		@Override
		public String toString() {
			return "nNodesI=" + nNodesInner
					+ ";nNodesL=" + nNodesLeaf
					+ ";capacityI=" + capacityInner
					+ ";capacityL=" + capacityLeaf
					+ ";nEntriesI=" + nEntriesInner
					+ ";nEntriesL=" + nEntriesLeaf
					+ ";fillRatioI=" + round(nEntriesInner/(double)capacityInner)
					+ ";fillRatioL=" + round(nEntriesLeaf/(double)capacityLeaf)
					+ ";fillRatio=" + round((nEntriesInner+nEntriesLeaf)/(double)(capacityInner+capacityLeaf));
		}
		private static double round(double d) {
			return ((int)(d*100+0.5))/100.;
		}
	}
	
	public Stats getStats() {
		Stats stats = new Stats();
		if (root != null) {
			root.getStats(stats);
		}
		return stats;
	}

	int getModCount() {
		return modCount;
	}
	
	public int size() {
		return nEntries;
	}

	void decreaseEntries() {
		nEntries--;
	}
	
	public enum REMOVE_OP {
		REMOVE_RETURN,
		KEEP_RETURN,
		KEEP_RETURN_NULL;
	}
}
