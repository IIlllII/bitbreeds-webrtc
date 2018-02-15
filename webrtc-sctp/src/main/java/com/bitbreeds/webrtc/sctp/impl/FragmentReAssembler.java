package com.bitbreeds.webrtc.sctp.impl;

import org.pcollections.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
public class FragmentReAssembler {

    private final DataStorage start;
    private final DataStorage end;

    private final PMap<Long,DataStorage> fragments;

    public static FragmentReAssembler empty() {
        return new FragmentReAssembler(null,null, HashTreePMap.empty());
    }

    private FragmentReAssembler(DataStorage start, DataStorage end, PMap<Long,DataStorage> fragments) {
        this.start = start;
        this.end = end;
        this.fragments = fragments;
    }

    public FragmentReAssembler addFragment(DataStorage frag)  {
        if(frag.getFlag().isStart()) {
            return new FragmentReAssembler(
                    frag,end,fragments);
        }
        else if(frag.getFlag().isEnd()) {
            return new FragmentReAssembler(
                    start,frag,fragments);
        }
        else if(frag.getFlag().isMiddle()) {
            return new FragmentReAssembler(
                    start,end,fragments.plus(frag.getTSN(),frag));
        }
        else {
            throw new IllegalStateException("Flag " +frag.getFlag()+ " is not a fragment");
        }
    }

    public Long firstTSN() {
        return this.start.getTSN();
    }

    public Long lastTSN() {
        return this.end.getTSN();
    }

    public List<DataStorage> completeOrderedMessage() {
        if(isComplete()) {
            ArrayList<DataStorage> orderedFragments = new ArrayList<DataStorage>();
            orderedFragments.add(start);
            List<DataStorage> sorted = fragments.values()
                    .stream().sorted()
                    .collect(Collectors.toList());
            orderedFragments.addAll(sorted);
            orderedFragments.add(end);
            return orderedFragments;
        }
        else {
            throw new IllegalStateException("Can not reassemble if not complete");
        }
    }

    public boolean isComplete() {
        boolean hasSeq = start != null && end != null;
        if(!hasSeq) {
            return false;
        }

        long midNum = lastTSN()-firstTSN()-1;
        return fragments.size() == midNum;
    }


}
