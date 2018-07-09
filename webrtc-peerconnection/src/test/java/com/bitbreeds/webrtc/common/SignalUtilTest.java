package com.bitbreeds.webrtc.common;

import com.bitbreeds.webrtc.common.SignalUtil;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Copyright (c) 12/05/16, Jonas Waage
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
public class SignalUtilTest {

    @Test
    public void testSignByte() {
        byte b = SignalUtil.sign(255);
        assertEquals(-1,b);

        b = SignalUtil.sign(0);
        assertEquals(0,b);

        b = SignalUtil.sign(254);
        assertEquals(-2,b);

        b = SignalUtil.sign(128);
        assertEquals(-128,b);

        b = SignalUtil.sign(192);
        assertEquals(-64,b);
    }

    @Test
    public void testByteConvert() {
        byte[] bt = SignalUtil.twoBytesFromInt((2 << 15)-1);
        assertEquals(-1,bt[0]);
        assertEquals(-1,bt[1]);

        bt = SignalUtil.twoBytesFromInt(0);
        assertEquals(0,bt[0]);
        assertEquals(0,bt[1]);

        bt = SignalUtil.twoBytesFromInt(511);
        assertEquals(1,bt[0]);
        assertEquals(-1,bt[1]);
    }

    @Test
    public void testArrayJoin() {
        byte[] a = {0,1};
        byte[] b = {2,3};
        byte[] c = {4,5};

        byte[] cmp = {0,1,2,3,4,5};

        byte[] cmp2 = {0,1,4,5,2,3};

        assertTrue(Arrays.equals(cmp, SignalUtil.joinBytesArrays(a,b,c)));
        assertTrue(Arrays.equals(cmp2, SignalUtil.joinBytesArrays(a,c,b)));
    }

    @Test
    public void testMultipleOfFour() {
        assertEquals(4, SignalUtil.multipleOfFour(4));
        assertEquals(8, SignalUtil.multipleOfFour(6));
        assertEquals(8, SignalUtil.multipleOfFour(7));
    }


    @Test
    public void fourBytesFromInt() {
        SignalUtil.fourBytesFromInt(0);

        byte[] a = {0,0,0,0};
        byte[] b = {0,0,0,5};
        byte[] c = {0,1,0,5};
        byte[] d = {2,1,0,5};
        assertTrue(Arrays.equals(a, SignalUtil.fourBytesFromInt(0)));

        assertTrue(Arrays.equals(b, SignalUtil.fourBytesFromInt(5)));

        int num = 0x00010005;
        byte[] ts = SignalUtil.fourBytesFromInt(num);
        assertTrue(Arrays.equals(c,ts));

        num = 0x02010005;
        ts = SignalUtil.fourBytesFromInt(num);
        assertTrue(Arrays.equals(d,ts));
    }


    @Test
    public void testLongByte() {
        long l = 1001223L;
        byte[] b = SignalUtil.longToBytes(l);
        long o = SignalUtil.bytesToLong(b);
        assertEquals(o,l);
    }


    @Test
    public void findIntegrity() throws DecoderException {

        String bytes = "000100502112a442e9dec49e8038338d00f9a79c0006001162323064306166343a623230643061663400000000250000002400046e7f00ff802a00081eb6ff2cf7589cd700080014020b27ff78fbd931427c9f4518b9f40d5415562e8028000473cf7c86";

        String noFinger2 = "000100482112a442e9dec49e8038338d00f9a79c0006001162323064306166343a623230643061663400000000250000002400046e7f00ff802a00081eb6ff2cf7589cd7";

        byte[] msg = Hex.decodeHex(noFinger2.toCharArray());

        byte[] mac = Hex.decodeHex("020b27ff78fbd931427c9f4518b9f40d5415562e".toCharArray());

        String password = "230f754083a9070aff5bd1ced7654a9c";

        byte[] compMac = SignalUtil.hmacSha1(msg,password.getBytes());

        System.out.println(Hex.encodeHexString(mac) +"  " + Hex.encodeHexString(compMac));
        assertTrue(Arrays.equals(mac,compMac));
    }

    @Test
    public void testSplit() {
        List<byte[]> res = SignalUtil.split(new byte[]{0,0,0,3,3,3,5},3);
        assertArrayEquals(Arrays.asList(new byte[]{0,0,0},new byte[]{3,3,3},new byte[]{5}).toArray(),res.toArray());
    }

    @Test
    public void testLongConv() {

        long l = SignalUtil.bytesToLong(SignalUtil.longToBytes(9000));
        assertEquals(l,9000L);

        long out = SignalUtil.bytesToLong(new byte[] {1,0});
        assertEquals(256L,out);
    }

}
