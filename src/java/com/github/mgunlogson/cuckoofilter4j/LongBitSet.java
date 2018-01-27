/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copied from Apache Harmony and Lucene (6.2.0) projects with modifications
 */
package com.github.mgunlogson.cuckoofilter4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BitSet of fixed length (numBits), backed by accessible ({@link #getBits})
 * long[], accessed with a long index. Use it only if you intend to store more
 * than 2.1B bits, otherwise you should use {@code FixedBitSet}.
 */
public final class LongBitSet implements Serializable {

    private static final Logger logger = LoggerFactory.getLogger(LongBitSet.class);

    /**
     *
     */
    private static final long serialVersionUID = 4332907629892263426L;
    public final AtomicLongArray bits; // Array of longs holding the bits
    private final long numBits; // The number of bits in use
    private final int numWords; // The exact number of longs needed to hold
    // numBits (<= bits.length)

    /**
     * If the given {@link LongBitSet} is large enough to hold
     * {@code numBits+1}, returns the given bits, otherwise returns a new
     * {@link LongBitSet} which can hold the requested number of bits.
     * <p>
     * <b>NOTE:</b> the returned bitset reuses the underlying {@code long[]} of
     * the given {@code bits} if possible. Also, calling {@link #length()} on
     * the returned bits may return a value greater than {@code numBits}.
     */
    static LongBitSet ensureCapacity(LongBitSet bits, long numBits) {
        if (numBits < bits.numBits) {
            return bits;
        }
        else {
            // Depends on the ghost bits being clear!
            // (Otherwise, they may become visible in the new instance)
            int numWords = bits2words(numBits);
            long[] arr = bits.getBits();
            if (numWords >= arr.length) {
                arr = ArrayUtil.grow(arr, numWords + 1);
            }
            return new LongBitSet(arr, (long) arr.length << 6);
        }
    }

    /**
     * returns the number of 64 bit words it would take to hold numBits
     */
    static int bits2words(long numBits) {
        return (int) ((numBits - 1) >> 6) + 1; // I.e.: get the word-offset of
        // the last bit and add one
        // (make sure to use >> so 0
        // returns 0!)
    }

    /**
     * Creates a new LongBitSet. The internally allocated long array will be
     * exactly the size needed to accommodate the numBits specified.
     *
     * @param numBits the number of bits needed
     */
    LongBitSet(long numBits) {
        this.numBits = numBits;
        bits = new AtomicLongArray(new long[bits2words(numBits)]);
        numWords = bits.length();
    }

    /**
     * Creates a new LongBitSet using the provided long[] array as backing
     * store. The storedBits array must be large enough to accommodate the
     * numBits specified, but may be larger. In that case the 'extra' or 'ghost'
     * bits must be clear (or they may provoke spurious side-effects)
     *
     * @param storedBits the array to use as backing store
     * @param numBits    the number of bits actually needed
     */
    LongBitSet(long[] storedBits, long numBits) {
        this.numWords = bits2words(numBits);
        if (numWords > storedBits.length) {
            throw new IllegalArgumentException("The given long array is too small  to hold " + numBits + " bits");
        }
        this.numBits = numBits;
        this.bits = new AtomicLongArray(storedBits);

        assert verifyGhostBitsClear();
    }

    /**
     * Checks if the bits past numBits are clear. Some methods rely on this
     * implicit assumption: search for "Depends on the ghost bits being clear!"
     *
     * @return true if the bits past numBits are clear.
     */
    private boolean verifyGhostBitsClear() {
        for (int i = numWords; i < bits.length(); i++) {
            if (bits.get(i) != 0) {
                return false;
            }
        }

        if ((numBits & 0x3f) == 0) {
            return true;
        }

        long mask = -1L << numBits;

        return (bits.get(numWords - 1) & mask) == 0;
    }

    /**
     * Returns the number of bits stored in this bitset.
     */
    long length() {
        return numBits;
    }

    /**
     * Expert.
     */
    long[] getBits() {
        long[] arr = new long[bits.length()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = bits.get(i);
        }

        return arr;
    }

    boolean get(long index) {
        assert index >= 0 && index < numBits : "index=" + index + ", numBits=" + numBits;
        int i = (int) (index >> 6); // div 64
        // signed shift will keep a negative index and force an
        // array-index-out-of-bounds-exception, removing the need for an
        // explicit check.
        long bitmask = 1L << index;
        long andOp = bits.get(i) & bitmask;
        boolean bitSet = andOp != 0;

//        logger.info("LongBitSet.get(); index={}; numBits={}; i={}; bits[i]={}; bitmask={}; andOp={}; returnVal={}; bits.hashCode={}; thread={}",
//                    index, numBits, i, bits.get(i), bitmask, andOp, bitSet, System.identityHashCode(bits), Thread.currentThread());

        return bitSet;
    }

    void set(long index) {
        assert index >= 0 && index < numBits : "index=" + index + " numBits=" + numBits;
        int i = (int) (index >> 6); // div 64
        long bitmask = 1L << index;
        long oldBitVal = bits.get(i);
        bits.set(i, bits.get(i) | bitmask);

//        logger.info("LongBitSet.set(); index={}; numBits={}; i={}; bits[i]-curr={}; bits[i]-new={}; bits.hashCode={}; thread={}",
//                    index, numBits, i, oldBitVal, bits.get(i), System.identityHashCode(bits), Thread.currentThread());
    }

    boolean getAndSet(long index) {
        assert index >= 0 && index < numBits : "index=" + index + ", numBits=" + numBits;
        int wordNum = (int) (index >> 6); // div 64
        long bitmask = 1L << index;
        boolean val = (bits.get(wordNum) & bitmask) != 0;
        bits.set(wordNum, bits.get(wordNum) | bitmask);
        return val;
    }

    void clear(long index) {
        assert index >= 0 && index < numBits : "index=" + index + ", numBits=" + numBits;
        int wordNum = (int) (index >> 6);
        long bitmask = 1L << index;
        bits.set(wordNum, bits.get(wordNum) & ~bitmask);
    }

    boolean getAndClear(long index) {
        assert index >= 0 && index < numBits : "index=" + index + ", numBits=" + numBits;
        int wordNum = (int) (index >> 6); // div 64
        long bitmask = 1L << index;
        boolean val = (bits.get(wordNum) & bitmask) != 0;
        bits.set(wordNum, bits.get(wordNum) & ~bitmask);
        return val;
    }

    /**
     * Returns the index of the first set bit starting at the index specified.
     * -1 is returned if there are no more set bits.
     */
    long nextSetBit(long index) {
        // Depends on the ghost bits being clear!
        assert index >= 0 && index < numBits : "index=" + index + ", numBits=" + numBits;
        int i = (int) (index >> 6);
        long word = bits.get(i) >> index; // skip all the bits to the right of index

        if (word != 0) {
            return index + Long.numberOfTrailingZeros(word);
        }

        while (++i < numWords) {
            word = bits.get(i);
            if (word != 0) {
                return (i << 6) + Long.numberOfTrailingZeros(word);
            }
        }

        return -1;
    }

    /**
     * Returns the index of the last set bit before or on the index specified.
     * -1 is returned if there are no more set bits.
     */
    long prevSetBit(long index) {
        assert index >= 0 && index < numBits : "index=" + index + " numBits=" + numBits;
        int i = (int) (index >> 6);
        final int subIndex = (int) (index & 0x3f); // index within the word
        long word = (bits.get(i) << (63 - subIndex)); // skip all the bits to the
        // left of index

        if (word != 0) {
            return (i << 6) + subIndex - Long.numberOfLeadingZeros(word); // See
            // LUCENE-3197
        }

        while (--i >= 0) {
            word = bits.get(i);
            if (word != 0) {
                return (i << 6) + 63 - Long.numberOfLeadingZeros(word);
            }
        }

        return -1;
    }

    /**
     * this = this OR other
     */
    void or(LongBitSet other) {
        assert other.numWords <= numWords : "numWords=" + numWords + ", other.numWords=" + other.numWords;
        int pos = Math.min(numWords, other.numWords);
        while (--pos >= 0) {
            bits.set(pos, bits.get(pos) | other.bits.get(pos));
        }
    }

    /**
     * this = this XOR other
     */
    void xor(LongBitSet other) {
        assert other.numWords <= numWords : "numWords=" + numWords + ", other.numWords=" + other.numWords;
        int pos = Math.min(numWords, other.numWords);
        while (--pos >= 0) {
            bits.set(pos, bits.get(pos) ^ other.bits.get(pos));
        }
    }

    /**
     * returns true if the sets have any elements in common
     */
    boolean intersects(LongBitSet other) {
        // Depends on the ghost bits being clear!
        int pos = Math.min(numWords, other.numWords);
        while (--pos >= 0) {
            if ((bits.get(pos) & other.bits.get(pos)) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * this = this AND other
     */
    void and(LongBitSet other) {
        int pos = Math.min(numWords, other.numWords);
        while (--pos >= 0) {
            bits.set(pos, bits.get(pos) & other.bits.get(pos));
        }
        if (numWords > other.numWords) {
            for (int i = other.numWords; i < numWords; i++) {
                bits.set(i, 0L);
            }
        }
    }

    /**
     * this = this AND NOT other
     */
    void andNot(LongBitSet other) {
        int pos = Math.min(numWords, other.numWords);
        while (--pos >= 0) {
            bits.set(pos, bits.get(pos) & ~other.bits.get(pos));
        }
    }

    /**
     * Scans the backing store to check if all bits are clear. The method is
     * deliberately not called "isEmpty" to emphasize it is not low cost (as
     * isEmpty usually is).
     *
     * @return true if all bits are clear.
     */
    boolean scanIsEmpty() {
        // This 'slow' implementation is still faster than any external one
        // could be
        // (e.g.: (bitSet.length() == 0 || bitSet.nextSetBit(0) == -1))
        // especially for small BitSets
        // Depends on the ghost bits being clear!
        final int count = numWords;

        for (int i = 0; i < count; i++) {
            if (bits.get(i) != 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Flips a range of bits
     *
     * @param startIndex lower index
     * @param endIndex   one-past the last bit to flip
     */
    void flip(long startIndex, long endIndex) {
        assert startIndex >= 0 && startIndex < numBits;
        assert endIndex >= 0 && endIndex <= numBits;
        if (endIndex <= startIndex) {
            return;
        }

        int startWord = (int) (startIndex >> 6);
        int endWord = (int) ((endIndex - 1) >> 6);

        /***
         * Grrr, java shifting uses only the lower 6 bits of the count so
         * -1L>>>64 == -1 for that reason, make sure not to use endmask if the
         * bits to flip will be zero in the last word (redefine endWord to be
         * the last changed...) long startmask = -1L << (startIndex & 0x3f); //
         * example: 11111...111000 long endmask = -1L >>> (64-(endIndex &
         * 0x3f)); // example: 00111...111111
         ***/

        long startmask = -1L << startIndex;
        long endmask = -1L >>> -endIndex; // 64-(endIndex&0x3f) is the same as
        // -endIndex since only the lowest 6
        // bits are used

        if (startWord == endWord) {
            bits.set(startWord, bits.get(startWord) ^ (startmask & endmask));
            return;
        }

        bits.set(startWord, bits.get(startWord) ^ startmask);

        for (int i = startWord + 1; i < endWord; i++) {
            bits.set(i, ~bits.get(i));
        }

        bits.set(endWord, bits.get(endWord) ^ endmask);
    }

    /**
     * Flip the bit at the provided index.
     */
    void flip(long index) {
        assert index >= 0 && index < numBits : "index=" + index + " numBits=" + numBits;
        int wordNum = (int) (index >> 6); // div 64
        long bitmask = 1L << index; // mod 64 is implicit
        bits.set(wordNum, bits.get(wordNum) ^ bitmask);
    }

    /**
     * Sets a range of bits
     *
     * @param startIndex lower index
     * @param endIndex   one-past the last bit to set
     */
    void set(long startIndex, long endIndex) {
        assert startIndex >= 0 && startIndex < numBits : "startIndex=" + startIndex + ", numBits=" + numBits;
        assert endIndex >= 0 && endIndex <= numBits : "endIndex=" + endIndex + ", numBits=" + numBits;
        if (endIndex <= startIndex) {
            return;
        }

        int startWord = (int) (startIndex >> 6);
        int endWord = (int) ((endIndex - 1) >> 6);

        long startmask = -1L << startIndex;
        long endmask = -1L >>> -endIndex; // 64-(endIndex&0x3f) is the same as
        // -endIndex since only the lowest 6
        // bits are used

        if (startWord == endWord) {
            bits.set(startWord, bits.get(startWord) | (startmask & endmask));
            return;
        }

        bits.set(startWord, bits.get(startWord) | startmask);
        for (int i = startWord + 1; i < endWord; i++) {
            bits.set(i, 0L);
        }
        bits.set(endWord, bits.get(endWord) | endmask);
    }

    /**
     * Clears a range of bits.
     *
     * @param startIndex lower index
     * @param endIndex   one-past the last bit to clear
     */
    void clear(long startIndex, long endIndex) {
        assert startIndex >= 0 && startIndex < numBits : "startIndex=" + startIndex + ", numBits=" + numBits;
        assert endIndex >= 0 && endIndex <= numBits : "endIndex=" + endIndex + ", numBits=" + numBits;
        if (endIndex <= startIndex) {
            return;
        }

        int startWord = (int) (startIndex >> 6);
        int endWord = (int) ((endIndex - 1) >> 6);

        long startmask = -1L << startIndex;
        long endmask = -1L >>> -endIndex; // 64-(endIndex&0x3f) is the same as
        // -endIndex since only the lowest 6
        // bits are used

        // invert masks since we are clearing
        startmask = ~startmask;
        endmask = ~endmask;

        if (startWord == endWord) {
            bits.set(startWord, bits.get(startWord) & (startmask | endmask));
            return;
        }

        bits.set(startWord, bits.get(startWord) & startmask);
        for (int i = startWord + 1; i < endWord; i++) {
            bits.set(i, 0L);
        }
        bits.set(endWord, bits.get(endWord) & endmask);
    }

    @Override
    public LongBitSet clone() {
        return new LongBitSet(getBits(), numBits);
    }

    /**
     * returns true if both sets have the same bits set
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LongBitSet)) {
            return false;
        }
        LongBitSet other = (LongBitSet) o;
        if (numBits != other.numBits) {
            return false;
        }
        // Depends on the ghost bits being clear!
        return bits.equals(other.bits);
    }

    @Override
    public int hashCode() {
        // Depends on the ghost bits being clear!
        long h = 0;
        for (int i = numWords; --i >= 0; ) {
            h ^= bits.get(i);
            h = (h << 1) | (h >>> 63); // rotate left
        }
        // fold leftmost bits into right and add a constant to prevent
        // empty sets from returning 0, which is too common.
        return (int) ((h >> 32) ^ h) + 0x98761234;
    }
}
