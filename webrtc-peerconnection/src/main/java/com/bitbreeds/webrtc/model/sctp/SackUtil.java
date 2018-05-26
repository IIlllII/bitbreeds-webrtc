package com.bitbreeds.webrtc.model.sctp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copyright (c) 07/06/16, Jonas Waage
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
public class SackUtil {

    public static List<GapAck> getGapAckList(Long newCumulativeTSN, Set<Long> tsns) {
        List<Long> ls = tsns.stream()
                .map(i -> i - newCumulativeTSN)
                .collect(Collectors.toList());

        if(ls.isEmpty()) {
            return new ArrayList<>();
        }

        Collections.sort(ls);

        List<GapAck> ackGroup = new ArrayList<>();
        if(ls.size() == 1) {
            ackGroup.add(new GapAck(ls.get(0),ls.get(0)));
            return ackGroup;
        }

        Long start = ls.get(0);
        Long prev = ls.get(0);
        for(int i = 1; i<ls.size(); i++) {
            Long now = ls.get(i);
            if(prev+1 == now) {
                prev = now;
            }
            else {
                ackGroup.add(new GapAck(start,prev));
                start = now;
                prev = now;
            }
            if(i == ls.size()-1) {
                ackGroup.add(new GapAck(start,prev));
            }
        }

        return ackGroup;
    }

}
