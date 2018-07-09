package com.bitbreeds.webrtc.sctp.impl;

import java.util.Optional;

/**
 * Copyright (c) 11/06/2018, Jonas Waage
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
public class SCTPReliability {

    public enum Type {TIME,RETRANSMITNUMBER}

    private final int param;
    private final Type type;

    private final boolean ordered;

    public static SCTPReliability createOrdered() {
        return new SCTPReliability(-1,null,true);
    }

    public static SCTPReliability createUnordered() {
        return new SCTPReliability(-1,null,false);
    }

    public static SCTPReliability createTimed(int param, boolean ordered) {
        return new SCTPReliability(param,Type.TIME,ordered);
    }

    public static SCTPReliability createMaxRetransmits(int param, boolean ordered) {
        return new SCTPReliability(param,Type.RETRANSMITNUMBER,ordered);
    }

    private SCTPReliability(int param, Type type, boolean ordered) {
        this.param = param;
        this.type = type;
        this.ordered = ordered;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public int getParam() {
        return param;
    }

    public Optional<Type> getType() {
        return Optional.ofNullable(type);
    }

    public boolean shouldAbandon(int number) {
        return number >= param;
    }


}
