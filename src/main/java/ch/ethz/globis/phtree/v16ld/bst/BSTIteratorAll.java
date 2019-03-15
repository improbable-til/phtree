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
	
	public BSTIteratorAll() {
		//nothing
	}
	
	public BSTIteratorAll reset(BSTreePage root) {
		this.currentPage = root;
		this.currentPos = 0;
		findNext();
		return this;
	}

	private void findNext() {
		while (currentPage != null ) {
			//first progress to next page, if necessary.
			if (currentPos >= currentPage.getNKeys()) {
				currentPage = null;
				currentPos = 0;
				continue;
			}

			nextValue = currentPage.getValues()[currentPos];
			currentPos++;
			return;
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