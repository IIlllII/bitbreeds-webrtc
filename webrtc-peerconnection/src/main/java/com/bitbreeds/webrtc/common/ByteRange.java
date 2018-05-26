package com.bitbreeds.webrtc.common;

/**
 * Copyright (c) 19/05/16, Jonas Waage
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


/**
 * Range from a to b.
 */
public class ByteRange {
    final int a;
    final int b;

    public ByteRange(int a, int b) {
        if(a > b ) {
            throw new IllegalArgumentException("ByteRange must be from lower number to higher");
        }
        this.a = a;
        this.b = b;
    }

    public ByteRange plus(int x) {
        return new ByteRange(a+x,b+x);
    }

    public ByteRange minus(int x) {
        return plus(-x);
    }

    public ByteRange lengthFromA(int lgt) {
        return new ByteRange(a,a+lgt);
    }

    @Override
    public String toString() {
        return "ByteRange{" +
                "a=" + a +
                ", b=" + b +
                '}';
    }

    public int getA() {
        return a;
    }

    public int getB() {
        return b;
    }
}
