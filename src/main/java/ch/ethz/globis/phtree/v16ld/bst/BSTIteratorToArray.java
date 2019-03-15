/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16ld.bst;

import ch.ethz.globis.phtree.v16ld.Node.BSTEntry;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorToArray {

	private BSTEntry[] entries;
	private int nEntries;

	
	public BSTIteratorToArray() {
		//nothing
	}
	
	public BSTIteratorToArray reset(BSTreePage root, BSTEntry[] entries) {
		this.entries = entries;
		this.nEntries = 0;

		//find first page
		readLeafPages(root);

		return this;
	}


	private void readLeafPages(BSTreePage currentPage) {
		while (currentPage != null) {
			BSTEntry[] values = currentPage.getValues();
			System.arraycopy(values, 0, entries, nEntries, currentPage.getNKeys());
			nEntries += currentPage.getNKeys();
			currentPage = null;
		}
	}
}
