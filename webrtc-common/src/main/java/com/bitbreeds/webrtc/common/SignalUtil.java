package com.bitbreeds.webrtc.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

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
public class SignalUtil {

    /**
     *
     * @param bytes bytes to convert to int
     * @return integer represented by bytes
     */
    public static int intFromTwoBytes(byte[] bytes) {
        if(bytes.length != 2) {
            throw  new IllegalArgumentException("Incorrect length size, expected 2 was: " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        byte[] zero = {0,0};
        buffer.put(joinBytesArrays(zero,bytes));
        buffer.flip();//need flip
        return buffer.getInt();
    }

    /**
     *
     * @param n int to convert
     * @return four bytes represented by int
     */
    public static byte[] fourBytesFromInt(int n) {
        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES);
        buff.putInt(n);
        return buff.array();
    }


    /**
     *
     * @param n int to convert
     * @return two bytes represented by int
     */
    public static byte[] twoBytesFromInt(int n) {
        if(n > (2 << 15)-1) {
            throw new IllegalArgumentException("Nr can not fit in two bytes, was" + n);
        }
        else if(n < 0) {
            throw new IllegalArgumentException("Nr can not be below zero, was" + n);
        }
        else {
            ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES);
            buff.putInt(n);
            return Arrays.copyOfRange(buff.array(),2,4);
        }
    }

    /**
     *
     * @param bytes bytes to make int from
     * @return integer value from four bytes
     */
    public static int intFromFourBytes(byte[] bytes) {
        if(bytes.length != 4) {
            throw  new IllegalArgumentException("Incorrect length size, expected 2 was: " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getInt();
    }

    /**
     * @param by byte to convert
     * @return java bytes are two's complement stuff
     */
    public static int unsign(byte by) {
        return by & 0xFF;
    }


    /**
     *
     * @param by int to convert
     * @return java bytes are two's complement stuff
     */
    public static byte sign(int by) {
        return (byte)(0xff & by);
    }


    /**
     *
     * @param args byte arrays to join
     * @return a single byte array from many
     */
    public static byte[] joinBytesArrays(byte[]... args) {
        return joinBytesArrays(Arrays.asList(args));
    }

    /**
     *
     * @param args byte arrays to join
     * @return a single byte array from many
     */
    public static byte[] joinBytesArrays(List<byte[]> args) {
        int n = args.stream().map(i->i.length).reduce(0,Integer::sum);
        byte[] out = new byte[n];

        int i = 0;
        for(byte[] arg:args){
            for(byte b:arg) {
                out[i] = b;
                i++;
            }
        }
        return out;
    }

    /**
     *
     * @param start the nr
     * @return the closest multiple of 4, if it is a multiple of 4 the start is returned
     */
    public static int multipleOfFour(int start) {
        if(start%4 != 0) {
            return ((start/4)+1)*4;
        }
        else {
            return start;
        }
    }


    public static byte[] xor(byte[] a, byte[] b) {
        if(a.length != b.length) {
            throw new IllegalArgumentException(
                    "Byte arrays must have same size, was: " +
                    a.length +" and " + b.length);
        }

        byte[] out = new byte[a.length];
        for(int i = 0; i<a.length; i++) {
            out[i] = sign( ((int)a[i]) ^ ((int)b[i]) );
        }
        return out;
    }

    /**
     *
     * @param bytes
     */
    public static long computeCRC32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.reset();
        crc.update(bytes);
        return crc.getValue();
    }


    /**
     *
     * @param bytes
     */
    public static long computeCRC32c(byte[] bytes) {
        CRC32c crc = new CRC32c();
        crc.reset();
        crc.update(bytes,0,bytes.length);
        return crc.getValue();
    }

    /**
     *
     * @param bytes
     * @return
     */
    public static byte[] flipBytes(byte[] bytes) {
        byte[] bt = new byte[bytes.length];
        for(int i = 0; i<bytes.length; i++) {
            bt[bytes.length-1-i] = bytes[i];
        }
        return bt;
    }

    /**
     *
     * @param l
     * @return
     */
    public static byte[] longToFourBytes(long l) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(l);
        return Arrays.copyOfRange(buffer.array(),4,8);
    }

    public static byte[] longToFourBytesFlip(long l) {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES*8);
        buffer.putLong(l);
        return Arrays.copyOfRange(buffer.array(),4,8);
    }

    /**
     *
     * @param l long to convert to bytes
     * @return 8 bytes representing long
     */
    public static byte[] longToBytes(long l) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(l);
        return buffer.array();
    }

    /**
     *
     * @param bytes bytes to pad
     * @return padded bytes
     */
    public static byte[] padToMultipleOfFour(byte[] bytes) {
        int nr = multipleOfFour(bytes.length);
        if(nr == bytes.length) {
            return bytes;
        }
        else {
            return Arrays.copyOf(bytes,nr);
        }
    }


    /**
     *
     * @param l bytes representing a long
     * @return long value represented by bytes
     */
    public static long bytesToLong(byte[] l) {
        if(l.length > 8) {
            throw new IllegalArgumentException("Too many bytes to create long");
        }
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        for(int i = 0; i<8-l.length; i++) {
            buffer.put((byte)0);
        }
        buffer.put(l);
        buffer.flip();//need flip
        return buffer.getLong();
    }


    /**
     *
     * @param key the key
     * @return md5 hash
     */
    public static byte[] md5key(byte[] key) {
        try {
            return MessageDigest.getInstance("MD5").digest(key);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create hash");
        }
    }

    /**
     *
     * @param data data to hash
     * @param key key used
     * @return created hmacsha1
     */
    public static byte[] hmacSha1(byte[] data, byte[] key) {
        try {
            // Get an hmac_sha1 key from the raw key bytes;
            SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
            // Get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            // Compute the hmac on input data bytes
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static ByteRange range(int a, int b){
        return new ByteRange(a,b);
    };

    public static byte[] copyRange(byte[] bytes,ByteRange range) {
        if(range.a < 0) {
            throw new ArrayIndexOutOfBoundsException("Range.a must be 0 or higher, was: " + range.a);
        }
        if(range.b > bytes.length) {
            throw new ArrayIndexOutOfBoundsException("Range.b must be smaller then array length "+bytes.length+", was: " + range.b);
        }
        return Arrays.copyOfRange(bytes,range.a,range.b);
    }

    public static List<byte[]> split(byte[] data,int chunkSize) {
        if(chunkSize < 1) {
            throw new IllegalArgumentException("Bad chunkzize " + chunkSize);
        }
        ArrayList<byte[]> split = new ArrayList<>();
        for(int i=0; i < data.length; i = i+chunkSize) {
            byte[] out = SignalUtil.copyRange(data, new ByteRange(i,Math.min(i+chunkSize,data.length)));
            split.add(out);
        }
        return split;
    }


    /**
     *
     * @param lgt length wanted
     * @return secure random bytes of length lgt
     */
    public static byte[] randomBytes(int lgt) {
        SecureRandom rd = new SecureRandom();
        rd.setSeed(System.nanoTime());
        byte[] bt = new byte[lgt];
        rd.nextBytes(bt);
        return bt;
    }

}
