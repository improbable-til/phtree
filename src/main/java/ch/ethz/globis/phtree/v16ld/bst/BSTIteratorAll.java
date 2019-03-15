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
public class BSTIteratorAll {


	private BSTreePage currentPage;
	private int currentPos;
	private BSTEntry nextValue;
	private int nFound = 0;
	
	public BSTIteratorAll() {
		//nothing
	}
	
	public BSTIteratorAll reset(BSTreePage root) {
		this.currentPage = root;
		this.currentPos = 0;
		this.nFound = 0;
		findNext();
		return this;
	}

	private void findNext() {
		if (currentPage.isAHC()) {
			int nValues = currentPage.getNKeys();
			BSTEntry[] values = currentPage.getValues();
			while (currentPos < values.length && nFound < nValues) {
				BSTEntry v = values[currentPos];
				currentPos++;
				if (v != null) {
					nFound++;
					nextValue = v;
					return;
				}
			}
			currentPage = null;
			currentPos = 0;
		} else {
			if (currentPos >= currentPage.getNKeys()) {
				currentPage = null;
				currentPos = 0;
			} else {
				nextValue = currentPage.getValues()[currentPos];
				currentPos++;
			}
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

}