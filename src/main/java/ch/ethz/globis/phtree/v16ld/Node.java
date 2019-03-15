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
package ch.ethz.globis.phtree.v16ld;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.util.unsynced.LongArrayOps;
import ch.ethz.globis.phtree.v16ld.PhTree16LD.UpdateInfo;
import ch.ethz.globis.phtree.v16ld.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16ld.bst.BSTreePage;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static ch.ethz.globis.phtree.PhTreeHelper.posInArray;


/**
 * Node of the PH-tree.
 * 
 * @author ztilmann
 */
public class Node {

	private int entryCnt = 0;

	/**
	 * postLenStored: Stored bits, including the hc address.
	 * postLenClassic: The number of postFix bits
	 * Rule: postLenClassic + 1 = postLenStored.  
	 */
	private byte postLenStored = 0;
	private byte infixLenStored = 0; //prefix size

	//Nested tree index
	private BSTreePage root;


    Node() {
		// For pooling only
	}

	private void initNode(int infixLenClassic, int postLenClassic, int dims, PhTree16LD<?> tree) {
		this.infixLenStored = (byte) (infixLenClassic + 1);
		this.postLenStored = (byte) (postLenClassic + 1);
		this.entryCnt = 0;
		this.root = bstCreateRoot(tree);
	}

	public static Node createNode(int dims, int infixLenClassic, int postLenClassic, PhTree16LD<?> tree) {
		Node n = tree.nodePool().get();
		n.initNode(infixLenClassic, postLenClassic, dims, tree);
		return n;
	}

	private void discardNode(PhTree16LD<?> tree) {
		entryCnt = 0;
		getRoot().clear();
		tree.bstPool().reportFreeNode(root);
		root = null;
		tree.nodePool().offer(this);
	}
	

	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param keyToMatch search key
     * @param newValueToInsert new value
	 * @param tree tree
	 * @return The sub node or null.
	 */
	Object doInsertIfMatching(long[] keyToMatch, Object newValueToInsert, PhTree16LD<?> tree) {
		int hcPos = toInt(posInArray(keyToMatch, getPostLen()));

		//ntPut will also increase the node-entry count
		Object v = addEntry(hcPos, keyToMatch, newValueToInsert, tree);
		//null means: Did not exist, or we had to do a split...
		if (v == null) {
			tree.increaseNrEntries();
		}
		return v;
	}

	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param keyToMatch The key of the entry
	 * @param getOnly True if we only get the value. False if we want to delete it.
	 * @param parent parent node
	 * @param insertRequired insertion info
	 * @param tree tree
	 * @return The sub node or null.
	 */
	Object doIfMatching(long[] keyToMatch, boolean getOnly, Node parent, UpdateInfo insertRequired, PhTree16LD<?> tree) {

		long hcPos = posInArray(keyToMatch, getPostLen());

		if (getOnly) {
			BSTEntry e = getEntry(hcPos, keyToMatch);
			return e != null ? e.getValue() : null;
		}
		Object v = removeEntry(hcPos, keyToMatch, insertRequired, tree);
		if (v != null && !(v instanceof Node)) {
			//Found and removed entry.
			tree.decreaseNrEntries();
			if (getEntryCount() == 1) {
				mergeIntoParentNt(keyToMatch, parent, tree);
			}
		}
		return v;
	}

	private long calcInfixMask(int subPostLen) {
		//We use a simplified mask, because the prefix is always present
		//long mask = ~((-1L)<<(getPostLen()-subPostLen-1));
		return (-1L) << (subPostLen+1);
	}
	

    /**
     * @param key1 key 1
     * @param val1 value 1
     * @param key2 key 2
     * @param val2 value 2
     * @param mcb  most conflicting bit
	 * @param tree tree
     * @return A new node or 'null' if there are no conflicting bits
     */
    public Node createNode(long[] key1, Object val1, long[] key2, Object val2, int mcb, PhTree16LD<?> tree) {
        //determine length of infix
        int newLocalInfLen = getPostLen() - mcb;
        int newPostLen = mcb - 1;
        Node newNode = createNode(key1.length, newLocalInfLen, newPostLen, tree);

		int posSub1 = toInt(posInArray(key1, newPostLen));
        int posSub2 = toInt(posInArray(key2, newPostLen));
		BSTEntry e1 = newNode.createEntry(posSub1, key1, val1, tree);
		BSTEntry e2 = newNode.createEntry(posSub2, key2, val2, tree);
       	newNode.root.init(e1, e2);
        newNode.entryCnt = 2;
        return newNode;
    }

	/**
	 * Writes a complete entry.
	 * This should only be used for new nodes.
	 *
	 * @param hcPos HC pos
	 * @param newKey new key
	 * @param value new value
	 */
	private BSTEntry createEntry(int hcPos, long[] newKey, Object value, PhTree16LD<?> tree) {
		if (value instanceof Node) {
			Node node = (Node) value;
			int newSubInfixLen = postLenStored() - node.postLenStored() - 1;
			node.setInfixLen(newSubInfixLen);
		}
		BSTEntry e = tree.bstPool().getEntry();
		e.set(hcPos, newKey, value);
		return e;
	}

	/**
     * @param v1 key 1
     * @param v2 key 2
     * @param mask bits to consider (1) and to ignore (0)
     * @return the position of the most significant conflicting bit (starting with 1) or
     * 0 in case of no conflicts.
     */
	private static int calcConflictingBits(long[] v1, long[] v2, long mask) {
		//long mask = (1l<<node.getPostLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
		//write all differences to diff, we just check diff afterwards
		long diff = 0;
		for (int i = 0; i < v1.length; i++) {
			diff |= (v1[i] ^ v2[i]);
		}
		return Long.SIZE-Long.numberOfLeadingZeros(diff & mask);
	}


	/**
	 * @param v1 key 1
	 * @param v2 key 2
	 * @return the position of the most significant conflicting bit (starting with 1) or
	 * 0 in case of no conflicts.
	 */
	public static int calcConflictingBits(long[] v1, long[] v2) {
		//long mask = (1l<<node.getPostLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
		//write all differences to diff, we just check diff afterwards
		long diff = 0;
		for (int i = 0; i < v1.length; i++) {
			diff |= (v1[i] ^ v2[i]);
		}
		return Long.SIZE-Long.numberOfLeadingZeros(diff);
	}


	private void mergeIntoParentNt(long[] key, Node parent, PhTree16LD<?> tree) {
		//check if merging is necessary (check children count || isRootNode)
		if (parent == null || getEntryCount() > 2) {
			//no merging required
			//value exists --> remove it
			return;
		}
		
		//okay, at his point we have a post that matches and (since it matches) we need to remove
		//the local node because it contains at most one other entry and it is not the root node.

		//We know that there is only a leaf node with only a single entry, so...
		BSTEntry nte = root.getFirstValue();
		
		int posInParent = toInt(PhTreeHelper.posInArray(key, parent.getPostLen()));
		if (nte.getValue() instanceof Node) {
			long[] newPost = nte.getKdKey();
			//connect sub to parent
			Node sub2 = (Node) nte.getValue();
			int newInfixLen = getInfixLen() + 1 + sub2.getInfixLen();
			sub2.setInfixLen(newInfixLen);

			//update parent, the position is the same
			//we use newPost as Infix
			//Replace sub!
			parent.replaceEntry(posInParent, newPost, sub2);
		} else {
			//this is also a post
			//Replace post!
			parent.replaceEntry(posInParent, nte.getKdKey(), nte.getValue());
		}

		//TODO return old key/BSTEntry to pool
		
		discardNode(tree);
	}

	static int toInt(long hcPos) {
		return (int) hcPos;
	}

	private static int N_GOOD = 0;
	private static int N = 0;
	
	private boolean checkInfix(int infixLen, long[] keyToTest, long[] rangeMin, long[] rangeMax) {
		//first check if node-prefix allows sub-node to contain any useful values

		if (PhTreeHelper.DEBUG) {
			N_GOOD++;
			//Ensure that we never enter this method if the node cannot possibly contain a match.
			long maskClean = mask1100(getPostLen());
			for (int dim = 0; dim < keyToTest.length; dim++) {
				if ((keyToTest[dim] & maskClean) > rangeMax[dim] || 
						(keyToTest[dim] | ~maskClean) < rangeMin[dim]) {
					if (getPostLen() < 63) {
						System.out.println("N-CAAI: " + ++N + " / " + N_GOOD);
						throw new IllegalStateException();
					}
					//ignore, this happens with negative values.
					//return false;
				}
			}
		}
		
		if (infixLen == 0) {
			return true;
		}

		//first, clean trailing bits
		//Mask for comparing the tempVal with the ranges, except for bit that have not been
		//extracted yet.
		long compMask = mask1100(postLenStored() - infixLen);
		for (int dim = 0; dim < keyToTest.length; dim++) {
			long in = keyToTest[dim] & compMask;
			if (in > rangeMax[dim] || in < (rangeMin[dim]&compMask)) {
				return false;
			}
		}

		return true;
	}

	private static long mask1100(int zeroBits) {
		return zeroBits == 64 ? 0 : ((-1L) << zeroBits);
	}
	
	/**
	 * Get post-fix.
	 * @param candidate candidate entry to check.
     * @param result return value
	 * @param rangeMin After the method call, this contains the postfix if the postfix matches the
	 * range. Otherwise it contains only part of the postfix.
     * @param rangeMax see rangeMin
	 * @return NodeEntry if the postfix matches the range, otherwise null.
	 */
	@SuppressWarnings("unchecked")
	<T> boolean checkAndGetEntry(BSTEntry candidate, PhEntry<T> result, long[] rangeMin, long[] rangeMax) {
		Object value = candidate.getValue();
		if (value instanceof Node) {
			Node sub = (Node) value;
			if (!checkInfix(sub.getInfixLen(), candidate.getKdKey(), rangeMin, rangeMax)) {
				return false;
			}
			result.setKeyInternal(candidate.getKdKey());
			result.setNodeInternal(sub);
			return true;
		} else if (LongArrayOps.checkRange(candidate.getKdKey(), rangeMin, rangeMax)) {
			result.setKeyInternal(candidate.getKdKey());
			result.setValueInternal((T) value);
			return true;
		} else {
			return false;
		}
	}


	/**
	 * @return entry counter
	 */
	public int getEntryCount() {
		return entryCnt;
	}


    public void decEntryCount() {
        --entryCnt;
    }

    public void decEntryCountGlobal(PhTree16LD<?> tree) {
        --entryCnt;
        tree.decreaseNrEntries();
    }


	public void incEntryCount() {
		++entryCnt;
	}

	public static void incEntryCountTree(PhTree16LD<?> tree) {
		tree.increaseNrEntries();
	}


	public int getInfixLen() {
		return infixLenStored() - 1;
	}

	private int infixLenStored() {
		return infixLenStored;
	}

    void setInfixLen(int newInfLen) {
        infixLenStored = (byte) (newInfLen + 1);
    }

    public int getPostLen() {
        return postLenStored - 1;
    }

	int postLenStored() {
		return postLenStored;
	}

    
    // ************************************
    // ************************************
    // BSTree
    // ************************************
    // ************************************
	
    public static int statNLeaves = 0;
	public static int statNInner = 0;
	
	private BSTreePage bstCreateRoot(PhTree16LD<?> tree) {
		//bootstrap index
		return bstCreatePage(tree);
	}


    public final BSTEntry bstGetOrCreate(long key, PhTree16LD<?> tree) {
        return getRoot().getOrCreate(toInt(key), this);
	}


    public BSTEntry bstRemove(long key, long[] kdKey, UpdateInfo ui, PhTree16LD<?> tree) {
		return getRoot().remove(key, kdKey, this, ui);
	}


    public <T> Object bstCompute(long key, long[] kdKey, boolean doIfAbsent,
								 BiFunction<long[], ? super T, ? extends T> mappingFunction) {
		return getRoot().computeLeaf(toInt(key), kdKey, this, doIfAbsent, mappingFunction);
    }


    public BSTEntry bstGet(long key) {
        return getRoot().getValueFromLeaf(key);
    }

	public BSTreePage bstCreatePage(PhTree16LD<?> tree) {
		return BSTreePage.create(tree);
	}

    public BSTreePage getRoot() {
        return root;
    }

    public String toStringTree() {
        StringBuilderLn sb = new StringBuilderLn();
        if (root != null) {
            root.toStringTree(sb, "");
        }
        return sb.toString();
    }


    public BSTIteratorAll iterator() {
        return new BSTIteratorAll().reset(getRoot());
    }

	
	public static class BSTStats {
		public int nNodesLeaf = 0;
		public int capacityLeaf = 0;
		public int nEntriesLeaf = 0;
		
		@Override
		public String toString() {
			return "nNodesL=" + nNodesLeaf
					+ ";capacityL=" + capacityLeaf
					+ ";nEntriesL=" + nEntriesLeaf
					+ ";fillRatioL=" + round(nEntriesLeaf/(double)capacityLeaf);
		}
		private static double round(double d) {
			return ((int)(d*100+0.5))/100.;
		}
	}
	
	public BSTStats getStats() {
		BSTStats stats = new BSTStats();
		if (root != null) {
			root.getStats(stats);
		}
		return stats;
	}

	
	// *****************************************
	// BST handler
	// *****************************************
	
	/**
	 * General contract:
	 * Returning a value or NULL means: Value was replaced, no change in counters
	 * Returning a Node means: Traversal not finished, no change in counters
	 * Returning null means: Insert successful, please update global entry counter
	 * 
	 * Node entry counters are updated internally by the operation
	 * Node-counting is done by the NodePool.
	 * 
	 * @param hcPos hc pos
	 * @param kdKey key
	 * @param value value
	 * @return see above
	 */
	Object addEntry(int hcPos, long[] kdKey, Object value, PhTree16LD<?> tree) {
		//Uses bstGetOrCreate() -> 
		//- get or create entry
		//- if value==null -> new entry, just set key,value
		//- if not null: decide to replacePos (exact match) or replaceWithSub 
		BSTEntry be = bstGetOrCreate(hcPos, tree);
		if (be.getKdKey() == null) {
			//new!
			be.set(hcPos, kdKey, value);
			return null;
		} 
		
		//exists!!
		return handleCollision(be, kdKey, value, tree);
	}


    private Object handleCollision(BSTEntry existingE, long[] kdKey, Object value, PhTree16LD<?> tree) {
        //We have two entries in the same location (local hcPos).
        //Now we need to compare the kdKeys.
        //If they are identical, we either replace the VALUE or return the SUB-NODE
        // (that's actually the same, simply return the VALUE)
        //If the kdKey differs, we have to split, insert a newSubNode and return null.

        Object localVal = existingE.getValue();
        if (localVal instanceof Node) {
            Node subNode = (Node) localVal;
            if (subNode.getInfixLen() > 0) {
                long mask = calcInfixMask(subNode.getPostLen());
                return insertSplit(existingE, kdKey, value, mask, tree);
            }
            //No infix conflict, just traverse subnode
            return localVal;
        } else {
            if (getPostLen() > 0) {
                return insertSplit(existingE, kdKey, value, -1L, tree);
            }
            //perfect match -> replace value
            existingE.set(existingE.getKey(), kdKey, value);
            return localVal;
        }
    }


    private Object insertSplit(BSTEntry currentEntry, long[] newKey, Object newValue, long mask, PhTree16LD<?> tree) {
		if (mask == 0) {
			//There won't be any split, no need to check.
			return currentEntry.getValue();
		}
		long[] localKdKey = currentEntry.getKdKey();
		Object currentValue = currentEntry.getValue();
		int maxConflictingBits = Node.calcConflictingBits(newKey, localKdKey, mask);
		if (maxConflictingBits == 0) {
			if (!(currentValue instanceof Node)) {
				//replace value
				currentEntry.set(currentEntry.getKey(), newKey, newValue);
			}
			//return previous value
			return currentValue;
		}

		Node newNode = createNode(newKey, newValue, localKdKey, currentValue, maxConflictingBits, tree);

		//replace value
		currentEntry.set(currentEntry.getKey(), tree.longPool().arrayClone(localKdKey), newNode);
		//entry did not exist
        return null;
	}
	
	private void replaceEntry(int hcPos, long[] kdKey, Object value) {
		BSTEntry be = bstGet(hcPos);
		be.set(hcPos, kdKey, value);
	}

	Object removeEntry(long hcPos, long[] keyToMatch, Node parent, PhTree16LD<?> tree) {
		Object v = removeEntry(hcPos, keyToMatch, (UpdateInfo)null, tree);
		if (v != null && !(v instanceof Node)) {
			//Found and removed entry.
			tree.decreaseNrEntries();
			if (getEntryCount() == 1) {
				mergeIntoParentNt(keyToMatch, parent, tree);
			}
		}
		return v;
	}

    <T> Object computeEntry(long hcPos, long[] keyToMatch, Node parent, PhTree16LD<?> tree,
                            boolean doIfAbsent, BiFunction<long[], ? super T, ? extends T> mappingFunction) {
        Object v = bstCompute(hcPos, keyToMatch, doIfAbsent, mappingFunction);
        //Check for removed elements
        if (getEntryCount() == 1) {
            mergeIntoParentNt(keyToMatch, parent, tree);
        }
        return v;
    }

    /**
     * General contract:
     * Returning a value or NULL means: Value was removed, please update global entry counter
     * Returning a Node means: Traversal not finished, no change in counters
     * Returning null means: Entry not found, no change in counters
     * <p>
     * Node entry counters are updated internally by the operation
     * Node-counting is done by the NodePool.
     *
     * @param hcPos hc pos
     * @param key   key
     * @param ui    UpdateInfo
     * @return See contract.
     */
    private Object removeEntry(long hcPos, long[] key, UpdateInfo ui, PhTree16LD<?> tree) {
        //Only remove value-entries, node-entries are simply returned without removing them
        BSTEntry prev = bstRemove(hcPos, key, ui, tree);
        //return values:
        // - null -> not found / remove failed
        // - Node -> recurse node
        // - T -> remove success
        //Node: removing a node is never necessary: When values are removed from the PH-Tree, nodes are replaced
        // with vales from sub-nodes, but they are never simply removed.
        //-> The BST.remove() needs to do:
        //  - Key not found: no delete, return null
        //  - No match: no delete, return null
        //  - Match Node: no delete, return Node
        //  - Match Value: delete, return value
        return prev == null ? null : prev.getValue();
    }

    public REMOVE_OP bstInternalRemoveCallback(BSTEntry currentEntry, long[] key, UpdateInfo ui) {
        if (matches(currentEntry, key)) {
            if (currentEntry.getValue() instanceof Node) {
                return REMOVE_OP.KEEP_RETURN;
            }
            if (ui != null) {
                //replace
                int bitPosOfDiff = Node.calcConflictingBits(key, ui.newKey, -1L);
                if (bitPosOfDiff <= getPostLen()) {
                    //replace
                    //simply replace kdKey!!
                    //Replacing the long[] should be correct (and fastest, and avoiding GC)
                    currentEntry.set(currentEntry.getKey(), ui.newKey, currentEntry.getValue());
                    return REMOVE_OP.KEEP_RETURN;
                } else {
                    ui.insertRequired = bitPosOfDiff;
                }
            }
            return REMOVE_OP.REMOVE_RETURN;
        }
        return REMOVE_OP.KEEP_RETURN_NULL;
    }


    BSTEntry getEntry(long hcPos, long[] keyToMatch) {
        BSTEntry be = bstGet(hcPos);
        if (be == null) {
            return null;
        }
        if (keyToMatch != null && !matches(be, keyToMatch)) {
            return null;
        }
        return be;
    }


    public boolean matches(BSTEntry be, long[] keyToMatch) {
        //This is always 0, unless we decide to put several keys into a single array
        if (be.getValue() instanceof Node) {
            Node sub = (Node) be.getValue();
            //TODO 2019 is this vectorizable?
            if (sub.getInfixLen() > 0) {
                final long mask = calcInfixMask(sub.getPostLen());
                return checkKdKey(be.getKdKey(), keyToMatch, mask);
            }
            return true;
        }

        return checkKdKey(be.getKdKey(), keyToMatch);
    }

    private static boolean checkKdKey(long[] allKeys, long[] keyToMatch, long mask) {
        for (int i = 0; i < keyToMatch.length; i++) {
            if (((allKeys[i] ^ keyToMatch[i]) & mask) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkKdKey(long[] allKeys, long[] keyToMatch) {
        for (int i = 0; i < keyToMatch.length; i++) {
            //TODO 2019
//            if (allKeys[i] != keyToMatch[i]) {
//                return false;
//            }
            if ((allKeys[i] ^ keyToMatch[i]) != 0) {
                return false;
            }
        }
        return true;
    }


	void getStats(PhTreeStats stats, List<BSTEntry> entries) {
		BSTIteratorAll iter = iterator();
		while (iter.hasNextEntry()) {
			entries.add(iter.nextEntry());
		}
		BSTStats bstStats = getStats();
		//nLeaf
		stats.nNT += bstStats.nNodesLeaf;
		//Capacity inner
		stats.nNtNodes += bstStats.capacityLeaf;
	}
	
		
	
	public enum REMOVE_OP {
		REMOVE_RETURN,
		KEEP_RETURN,
		KEEP_RETURN_NULL
	}

    public static class BSTEntry {
        private int key;
        private long[] kdKey;
        private Object value;

        public BSTEntry(int key, long[] k, Object v) {
            this.key = key;
            kdKey = k;
            value = v;
        }

        public BSTEntry() {
            //for pool
        }

        public int getKey() {
            return key;
        }

        public long[] getKdKey() {
            return kdKey;
        }

        public Object getValue() {
            return value;
        }

        public void set(int key, long[] kdKey, Object value) {
            this.key = key;
            this.kdKey = kdKey;
            this.value = value;
        }

        @Override
        public String toString() {
            return (kdKey == null ? null : Arrays.toString(kdKey)) + "->" + value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }

}
