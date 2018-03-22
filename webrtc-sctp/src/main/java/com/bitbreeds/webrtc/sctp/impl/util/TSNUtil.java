package com.bitbreeds.webrtc.sctp.impl.util;

/**
 * Copyright (c) 14/02/2018, Jonas Waage
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class TSNUtil {

    /**
     * Accepted as a duplicate if within this range,
     * otherwise new message.
     */
    static final int TSN_DIFF = 1000000000;


    /**
     *
     * @param a a TSN
     * @param b a TSN
     * @return distance, if the two TSNs are too far apart,the TSN has looped.
     */
    public static long cmp(long a,long b) {
        if(Math.abs(a-b) < TSN_DIFF) {
            return Math.min(a,b);
        }
        else {
            return Math.max(a,b);
        }
    }



    /**
     *
     * @param tsn tsn
     * @param min min tsn given
     * @return whether we are below the given tsn or too far away.
     */
     public static boolean isBelow(long tsn,long min) {
        return tsn < min && Math.abs(tsn-min) < TSN_DIFF;
    }

}
