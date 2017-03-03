package com.bitbreeds.webrtc.stun;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

/**
 * Copyright (c) 11/05/16, Jonas Waage
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
public class StunTest {

    @Test
    public void testStunConversion() {

        StunRequestTypeEnum en = StunRequestTypeEnum.fromBytes(new byte[] {0,1});
        assertEquals(StunRequestTypeEnum.BINDING_REQUEST,en);

        en = StunRequestTypeEnum.fromBytes(new byte[] {1,1});
        assertEquals(StunRequestTypeEnum.BINDING_RESPONSE,en);

        en = StunRequestTypeEnum.fromBytes(new byte[] {1,17});
        assertEquals(StunRequestTypeEnum.BINDING_ERROR_RESPONSE,en);

    }


    @Test
    public void testStunNoDuplicates() {
        final HashSet<Integer> set = new HashSet<>();
        Arrays.stream(StunAttributeTypeEnum.values())
                .forEach(i-> set.add(i.getNr()));

        assertEquals(StunAttributeTypeEnum.values().length,set.size());

        set.clear();
        Arrays.stream(StunRequestTypeEnum.values())
                .forEach(i-> set.add(i.getNr()));

        assertEquals(StunRequestTypeEnum.values().length,set.size());
    }


    @Test
    public void testStunAttribConversion() {

        StunAttributeTypeEnum en = StunAttributeTypeEnum.fromInt(6);
        assertEquals(StunAttributeTypeEnum.USERNAME,en);

        en = StunAttributeTypeEnum.fromBytes(new byte[] {0,6});
        assertEquals(StunAttributeTypeEnum.USERNAME,en);

    }

}
