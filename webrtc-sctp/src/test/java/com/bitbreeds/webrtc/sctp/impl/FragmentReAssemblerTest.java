package com.bitbreeds.webrtc.sctp.impl;

import com.bitbreeds.webrtc.common.SCTPPayloadProtocolId;
import com.bitbreeds.webrtc.sctp.model.SCTPOrderFlag;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
public class FragmentReAssemblerTest {

    @Test
    public void testReAssembly() {
        FragmentReAssembler ass = FragmentReAssembler.empty();

        assertFalse(ass.isComplete());

        DataStorage start = new DataStorage(
                1,
                40,
                22,
                SCTPOrderFlag.UNORDERED_START_FRAGMENT, SCTPPayloadProtocolId.WEBRTC_BINARY,new byte[]{6,6,6});

        ass = ass.addFragment(start);
        assertFalse(ass.isComplete());

        DataStorage mid = new DataStorage(
                2,
                40,
                22,
                SCTPOrderFlag.UNORDERED_MIDDLE_FRAGMENT, SCTPPayloadProtocolId.WEBRTC_BINARY,new byte[]{1,1,1});

        ass = ass.addFragment(mid);
        assertFalse(ass.isComplete());

        DataStorage end = new DataStorage(
                3,
                40,
                22,
                SCTPOrderFlag.UNORDERED_END_FRAGMENT, SCTPPayloadProtocolId.WEBRTC_BINARY,new byte[]{0,0,0});

        ass = ass.addFragment(end);
        assertTrue(ass.isComplete());

        assertEquals(Arrays.asList(start,mid,end),ass.completeOrderedMessage());


    }




    @Test
    public void testReAssemblyMultiMid() {
        FragmentReAssembler ass = FragmentReAssembler.empty();

        assertFalse(ass.isComplete());

        DataStorage mid = new DataStorage(
                2,
                40,
                22,
                SCTPOrderFlag.UNORDERED_MIDDLE_FRAGMENT, SCTPPayloadProtocolId.WEBRTC_BINARY,new byte[]{1,1,1});

        ass = ass.addFragment(mid);
        assertFalse(ass.isComplete());

        DataStorage end = new DataStorage(
                4,
                40,
                22,
                SCTPOrderFlag.UNORDERED_END_FRAGMENT, SCTPPayloadProtocolId.WEBRTC_BINARY,new byte[]{0,0,0});

        ass = ass.addFragment(end);
        assertFalse(ass.isComplete());

        DataStorage start = new DataStorage(
                1,
                40,
                22,
                SCTPOrderFlag.UNORDERED_START_FRAGMENT, SCTPPayloadProtocolId.WEBRTC_BINARY,new byte[]{6,6,6});

        ass = ass.addFragment(start);
        assertFalse(ass.isComplete());

        DataStorage mid2 = new DataStorage(
                3,
                40,
                22,
                SCTPOrderFlag.UNORDERED_MIDDLE_FRAGMENT, SCTPPayloadProtocolId.WEBRTC_BINARY,new byte[]{4,4,4});

        ass = ass.addFragment(mid2);
        assertTrue(ass.isComplete());

        assertEquals(Arrays.asList(start,mid,mid2,end),ass.completeOrderedMessage());


    }


}
