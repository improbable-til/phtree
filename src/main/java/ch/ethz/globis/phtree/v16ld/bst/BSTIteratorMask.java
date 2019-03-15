/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16ld.bst;

import ch.ethz.globis.phtree.v16ld.Node.BSTEntry;

import java.util.NoSuchElementException;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorMask {

	private BSTreePage currentPage = null;
	private int currentPos = 0;
	private long minMask;
	private long maxMask;
	private BSTEntry nextValue;
	private int nFound = 0;
 	
	public BSTIteratorMask() {
		//nothing
	}

	public BSTIteratorMask reset(BSTreePage root, long minMask, long maxMask, int nEntries) {
		this.minMask = minMask;
		this.maxMask = maxMask;
		this.currentPage = root;
		if (root.isAHC()) {
			this.currentPos = (int) minMask;
		} else {
			this.currentPos = 0;
		}
		this.nFound = 0;

		//special optimization if only one quadrant matches
		if (nEntries > 4 && Long.bitCount(minMask ^ maxMask) == 0) {
			currentPos = currentPage.binarySearch(minMask);
			if (currentPos >= 0) {
				nextValue = currentPage.getValues()[currentPos];
			} else {
				currentPage = null;
			}
			currentPos = Integer.MAX_VALUE;
			return this;
		}
		
		findNext();
		return this;
	}

	private void findNext() {
		if (currentPage.isAHC()) {
			int nValues = currentPage.getNKeys();
			BSTEntry[] values = currentPage.getValues();
			while (currentPos < values.length && nFound < nValues) {
				BSTEntry v = values[currentPos];
				if (v != null) {
					nFound++;
					int key = v.getKey();
					if (check(key)) {
						nextValue = v;
						currentPos++;
						return;
					} else if (key > maxMask) {
						currentPage = null;
						return;
					}
				}
				currentPos++;
			}
			currentPage = null;
			currentPos = 0;
		} else {
			int nKeys = currentPage.getNKeys();
			long[] keys = currentPage.getKeys();
			while (currentPos < nKeys) {
				long key = keys[currentPos];
				if (check(key)) {
					nextValue = currentPage.getValues()[currentPos];
					currentPos++;
					return;
				} else if (key > maxMask) {
					currentPage = null;
					return;
				}
				currentPos++;
			}
			currentPage = null;
			currentPos = 0;
		}
	}
	

	public boolean hasNextEntry() {
		return currentPage != null;
	}
	
	public BSTEntry nextEntry() {
		if (!hasNextEntry()) {
			throw new NoSuchElementException();
		}

        BSTEntry ret = nextValue;
		findNext();
		return ret;
	}

	public void adjustMinMax(long maskLower, long maskUpper) {
		this.minMask = maskLower;
		this.maxMask = maskUpper;
	}

	
	private boolean check(long key) {
		return ((key | minMask) & maxMask) == key;
	}
}
