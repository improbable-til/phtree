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

import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.v16ld.Node;
import ch.ethz.globis.phtree.v16ld.Node.BSTEntry;
import ch.ethz.globis.phtree.v16ld.Node.BSTStats;
import ch.ethz.globis.phtree.v16ld.Node.REMOVE_OP;
import ch.ethz.globis.phtree.v16ld.PhTree16LD;

import java.util.Arrays;
import java.util.function.BiFunction;


public class BSTreePageLHC {

	private static final int INITIAL_PAGE_SIZE = 4;

	private long[] keys;
	private BSTEntry[] values;
	private short nEntries;

	private final PhTree16LD<?> tree;



	BSTreePageLHC(PhTree16LD<?> tree) {
	    this.tree = tree;
		init(tree.getDim());
	}
	
	void init(int dims) {
		nEntries = 0;
		//TODO other intitial page size?
		int initialPageSize = (1 << dims) <= 8 ? 2 : INITIAL_PAGE_SIZE;
		keys = tree.bstPool().arrayCreateLong(initialPageSize);
		values = tree.bstPool().arrayCreateEntries(initialPageSize);
		Node.statNLeaves++;
	}

	public void init(BSTEntry e1, BSTEntry e2) {
		if (nEntries > 0) {
			throw new IllegalStateException("nEntries=" + nEntries);
		}
		values[0] = e1;
		keys[0] = e1.getKey();
		values[1] = e2;
		keys[1] = e2.getKey();
		nEntries = 2;
	}

	public static BSTreePage create(PhTree16LD<?> tree) {
		return tree.bstPool().getNode(tree);
	}

	public BSTEntry getValueFromLeaf(long key) {
		int pos = binarySearch(key);
		return pos >= 0 ? values[pos] : null;
	}

	/**
	 * Binary search.
	 * 
	 * @param key search key
	 */
	int binarySearch(long key) {
		if (nEntries <=8) {
			return linearSearch(key);
		}
		long[] keys = this.keys;
		int low = 0;
		int high = nEntries - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
        	long midVal = keys[mid];

        	if (midVal < key)
        		low = mid + 1;
        	else if (midVal > key)
        		high = mid - 1;
        	else {
       			return mid; // key found
        	}
		}
		return -(low + 1);  // key not found.
	}

	private int linearSearch(long key) {
		long[] keys = this.keys;
		for (int i = 0; i < nEntries; i++) {
			if (key <= keys[i]) {
				return key == keys[i] ? i : -(i+1);
			}
		}
		return -(nEntries+1);  // key not found.
	}

	private void ensureSizePlusOne(Node ind) {
		if (nEntries + 1 > keys.length) {
			int newLen = keys.length*2; //TODO  > ind.maxLeafN() ? ind.maxLeafN() : keys.length*2;
			keys = tree.bstPool().arrayExpand(keys, newLen);
			values = tree.bstPool().arrayExpand(values, newLen);
		}
	}

	public final BSTEntry getOrCreate(int key, Node ind) {
		//in any case, check whether the key(+value) already exists
        int pos = binarySearch(key);
        //key found? -> pos >=0
        if (pos >= 0) {
        	return values[pos];
        }
		return create(key, pos, ind);
	}

	private BSTEntry create(int key, int pos, Node ind) {
		ensureSizePlusOne(ind);

		BSTEntry entry = tree.bstPool().getEntry();
		entry.set(key, null, null);
		pos = -(pos+1);
		if (pos < nEntries) {
			System.arraycopy(keys, pos, keys, pos+1, nEntries-pos);
			System.arraycopy(values, pos, values, pos+1, nEntries-pos);
		}
		keys[pos] = key;
		values[pos] = entry;
		nEntries++;
		ind.incEntryCount();
		return entry;
 	}

	public void print(String indent) {
		System.out.println(indent + "Leaf page: nK=" + nEntries + " keys=" + Arrays.toString(keys));
		System.out.println(indent + "                         " + Arrays.toString(values));
	}

	public void toStringTree(StringBuilderLn sb, String indent) {
		sb.appendLn(indent + "Leaf page: nK=" + nEntries + " keys=" + Arrays.toString(keys));
		sb.appendLn(indent + "                         " + Arrays.toString(values));
	}

	public short getNKeys() {
		return nEntries;
	}

	public BSTEntry remove(long key, long[] kdKey, Node node, PhTree16LD.UpdateInfo ui) {
		int i = binarySearch(key);
		if (i < 0) {
			//key not found
			return null;
		}

		// first remove the element
		BSTEntry prevValue = values[i];
		REMOVE_OP op = node.bstInternalRemoveCallback(prevValue, kdKey, ui);
		switch (op) {
			case REMOVE_RETURN:
				System.arraycopy(keys, i+1, keys, i, nEntries-i-1);
				System.arraycopy(values, i+1, values, i, nEntries-i-1);
				nEntries--;
				node.decEntryCount();
				return prevValue;
			case KEEP_RETURN:
				return prevValue;
			case KEEP_RETURN_NULL:
				return null;
			default:
				throw new IllegalArgumentException();
		}
	}


	public <T> Object computeLeaf(int key, long[] kdKey, Node node,
                                    boolean doIfAbsent, BiFunction<long[], ? super T, ? extends T> mappingFunction) {
		int pos = binarySearch(key);
		if (pos < 0) {
			//key not found
			if (doIfAbsent) {
				T newValue = mappingFunction.apply(kdKey, null);
				if (newValue != null) {
					BSTEntry e = addForCompute(key, pos, node);
					e.set(key, kdKey, newValue);
					return newValue;
				}
			}
			return null;
		}

		BSTEntry currentEntry = values[pos];
		Object currentValue = currentEntry.getValue();
		if (currentValue instanceof Node) {
			if (((Node) currentValue).getInfixLen() == 0) {
				//Shortcut that avoid MCB calculation: No infix conflict, just traverse the subnode (=currentValue)
				return currentValue;
			}
		}

		long[] localKdKey = currentEntry.getKdKey();
        int maxConflictingBits = Node.calcConflictingBits(kdKey, localKdKey);
        if (maxConflictingBits == 0) {
            if (currentValue instanceof Node) {
                //return entry with subnode
                return currentValue;
            }
            T newValue = mappingFunction.apply(kdKey, PhTreeHelper.unmaskNull(currentEntry.getValue()));
            if (newValue == null) {
                //remove
                removeForCompute(pos, node);
                tree.bstPool().offerEntry(currentEntry);
                return null;
            } else {
                //replace (cannot be null)
                currentEntry.setValue(newValue);
            }
            return newValue;
        }

		if (currentValue instanceof Node) {
			Node subNode = (Node) currentValue;
			if (subNode.getPostLen() + 1 >= maxConflictingBits) {
				return subNode;
			}
		}

		//Key found, but entry does not match
        if (doIfAbsent) {
            //We have two entries in the same location (local hcPos).
            //If the kdKey differs, we have to split, insert a newSubNode and return null.
			T newValue = mappingFunction.apply(kdKey, null);
			if (newValue != null) {
				insertSplit(currentEntry, kdKey, newValue, tree, maxConflictingBits, node);
				return newValue;
			}
			return null;
        }
        //Return 'null' when ignoring absent values
        return null;
	}

	private BSTEntry addForCompute(int key, int pos, Node node) {
		BSTEntry o = create(key, pos, node);
		Node.incEntryCountTree(tree);
		return o;
	}

	private void removeForCompute(int pos, Node node) {
		System.arraycopy(keys, pos+1, keys, pos, nEntries-pos-1);
		System.arraycopy(values, pos+1, values, pos, nEntries-pos-1);
		nEntries--;
		node.decEntryCountGlobal(tree);
	}


    private void insertSplit(BSTEntry currentEntry, long[] newKey, Object newValue, PhTree16LD<?> tree,
                               int maxConflictingBits, Node node) {
        long[] localKdKey = currentEntry.getKdKey();
        Node newNode = node.createNode(newKey, newValue, localKdKey, currentEntry.getValue(), maxConflictingBits, tree);
        //replace local entry with new subnode
        currentEntry.set(currentEntry.getKey(), tree.longPool().arrayClone(localKdKey), newNode);
        Node.incEntryCountTree(tree);
    }

	final long[] getKeys() {
		return keys;
	}

	final BSTEntry[] getValues() {
		return values;
	}

	public final void clear() {
		nEntries = -1;
	}

	public void getStats(BSTStats stats) {
		stats.nNodesLeaf++;
		stats.nEntriesLeaf += nEntries;
		stats.capacityLeaf += keys.length;
	}

	public BSTEntry getFirstValue() {
		return values[0];
	}

	void nullify() {
		keys = null;
		values = null;
		nEntries = 0;
	}
}
