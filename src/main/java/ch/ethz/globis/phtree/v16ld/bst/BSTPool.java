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
package ch.ethz.globis.phtree.v16ld.bst;

import ch.ethz.globis.phtree.util.unsynced.LongArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectPool;
import ch.ethz.globis.phtree.v16ld.Node.BSTEntry;
import ch.ethz.globis.phtree.v16ld.Node;
import ch.ethz.globis.phtree.v16ld.PhTree16LD;

public class BSTPool {

    private final ObjectArrayPool<BSTEntry> entryArrayPool = ObjectArrayPool.create(n -> new BSTEntry[n]);
    private final LongArrayPool keyPool = LongArrayPool.create();
	private final ObjectPool<BSTreePage> pagePool = ObjectPool.create(null);
	private final ObjectPool<BSTEntry> entryPool = ObjectPool.create(BSTEntry::new);

    public static BSTPool create(){
    	return new BSTPool();
	}

    private BSTPool() {
    	// empty
    }

    /**
     * Create an array.
     * @param newSize size
     * @return New array.
     */
    BSTEntry[] arrayCreateEntries(int newSize) {
    	return entryArrayPool.getArray(newSize);
	}

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public BSTEntry[] arrayExpand(BSTEntry[] oldA, int newSize) {
    	BSTEntry[] newA = entryArrayPool.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	entryArrayPool.offer(oldA);
    	return newA;
	}


    /**
     * Create a new array.
     * @param newSize size
     * @return New array.
     */
    long[] arrayCreateLong(int newSize) {
    	return keyPool.getArray(newSize);
	}


    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public long[] arrayExpand(long[] oldA, int newSize) {
    	long[] newA = keyPool.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	keyPool.offer(oldA);
    	return newA;
	}

	public void reportFreeNode(BSTreePage p) {
		keyPool.offer(p.getKeys());
		entryArrayPool.offer(p.getValues());
		p.nullify();
		pagePool.offer(p);
	}

	public BSTreePage getNode(PhTree16LD<?> tree) {
		BSTreePage p = pagePool.get();
		if (p != null) {
			p.init(tree.getDim());
			return p;
		}
		return new BSTreePage(tree);
	}

	public BSTEntry getEntry() {
    	return entryPool.get();
	}

	void offerEntry(BSTEntry entry) {
    	entry.set(0, null, null);
    	entryPool.offer(entry);
	}
}
