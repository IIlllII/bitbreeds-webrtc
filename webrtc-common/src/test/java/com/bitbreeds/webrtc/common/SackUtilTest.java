package com.bitbreeds.webrtc.common;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static com.bitbreeds.webrtc.common.SackUtil.getGapAckList;
import static org.junit.Assert.assertEquals;

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
public class SackUtilTest {

    @Test
    public void testGapAckGen() {
        HashSet<Long> ls = new HashSet<>();
        ls.add(0L);
        ls.add(1L);
        ls.add(2L);
        ls.add(3L);
        ls.add(4L);

        ls.add(6L);
        ls.add(7L);

        ls.add(10L);

        List<GapAck> ackList = getGapAckList(ls);

        assertEquals(3,ackList.size());

        GapAck a = ackList.get(0);
        GapAck b = ackList.get(1);
        GapAck c = ackList.get(2);

        assertEquals(a.start,0L);
        assertEquals(a.end,4L);

        assertEquals(b.start,6L);
        assertEquals(b.end,7L);

        assertEquals(c.start,10L);
        assertEquals(c.end,10L);

    }


    @Test
    public void testGapAckGenEmpty() {
        HashSet<Long> ls = new HashSet<>();

        List<GapAck> ackList = getGapAckList(ls);

        assertEquals(0,ackList.size());
    }



    @Test
    public void testGapAckGenOne() {
        HashSet<Long> ls = new HashSet<>();
        ls.add(0L);
        ls.add(1L);
        ls.add(2L);
        ls.add(3L);
        ls.add(4L);


        List<GapAck> ackList = getGapAckList(ls);

        assertEquals(1,ackList.size());

        GapAck a = ackList.get(0);

        assertEquals(a.start,0L);
        assertEquals(a.end,4L);
    }


    @Test
    public void testGapAckGenSingle() {
        HashSet<Long> ls = new HashSet<>();
        ls.add(0L);

        List<GapAck> ackList = getGapAckList(ls);

        assertEquals(1,ackList.size());

        GapAck a = ackList.get(0);

        assertEquals(a.start,0L);
        assertEquals(a.end,0L);
    }



    @Test
    public void testGapAckGenSeveralSingle() {
        HashSet<Long> ls = new HashSet<>();
        ls.add(0L);
        ls.add(10L);
        ls.add(20L);

        List<GapAck> ackList = getGapAckList(ls);

        assertEquals(3,ackList.size());

        GapAck a = ackList.get(0);
        GapAck b = ackList.get(1);
        GapAck c = ackList.get(2);

        assertEquals(a.start,0L);
        assertEquals(a.end,0L);

        assertEquals(b.start,10L);
        assertEquals(b.end,10L);

        assertEquals(c.start,20L);
        assertEquals(c.end,20L);
    }
}
