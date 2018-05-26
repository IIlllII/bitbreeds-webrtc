package com.bitbreeds.webrtc.stun;

import com.bitbreeds.webrtc.common.SignalUtil;
import org.apache.commons.codec.binary.Hex;

import java.util.*;
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

/**
 * @see <a href=https://tools.ietf.org/html/rfc5389#section-18.2>rfc5389</a>
 */
public class StunMessage {

    private static final int HEADER_LENGTH_BYTES = 20;

    private final StunHeader header;

    private final Map<StunAttributeTypeEnum,StunAttribute> attributeSet;

    private final boolean withIntegrity;

    private final boolean withFingerprint;

    private final String password;

    private final String username;

    /**
     * @param type          {@link StunRequestTypeEnum}
     * @param cookie        see rfc5389
     * @param transactionId see rfc5389
     * @param attr          {@Link StunAttributeTypeEnum}
     * @return StunMessage to operate on.
     */
    public static StunMessage fromData(
            StunRequestTypeEnum type,
            byte[] cookie,
            byte[] transactionId,
            Map<StunAttributeTypeEnum,StunAttribute> attr,
            boolean withIntegrity,
            boolean withFingerprint,
            String username,
            String password
    ) {

        //attr.add(new StunAttribute(StunAttributeTypeEnum.PASSWORD, password.getBytes()));

        attr.remove(StunAttributeTypeEnum.MESSAGE_INTEGRITY);
        attr.remove(StunAttributeTypeEnum.FINGERPRINT);
        attr.remove(StunAttributeTypeEnum.ICE_CONTROLLING);
        attr.remove(StunAttributeTypeEnum.USE_CANDIDATE);
        attr.remove(StunAttributeTypeEnum.PRIORITY);
        attr.remove(StunAttributeTypeEnum.USERNAME);

        int lgt = attr.values().stream()
                .map(i -> SignalUtil.multipleOfFour(4 + i.getLength())) //Lengths to multiple of 4 + 4 for lgt and type
                .reduce(0, Integer::sum); //Sum integers

        return new StunMessage(
                new StunHeader(type, lgt, cookie, transactionId),
                attr,
                withIntegrity,
                withFingerprint,
                username,
                password);
    }

    /**
     * @param data bytes that make up StunMessage
     * @return parsed StunMessage
     */
    public static StunMessage fromBytes(byte[] data) {
        StunHeader header = StunHeader.fromBytes(Arrays.copyOfRange(data, 0, HEADER_LENGTH_BYTES));

        Map<StunAttributeTypeEnum,StunAttribute> attributeMap = new HashMap<>();
        int start = HEADER_LENGTH_BYTES;

        while (start - HEADER_LENGTH_BYTES < header.getMessageLength() && (start) < data.length) {
            byte[] tp = Arrays.copyOfRange(data, start, start + 2);
            StunAttributeTypeEnum type = StunAttributeTypeEnum.fromBytes(tp);
            start += 2;

            int lgt = SignalUtil.intFromTwoBytes(Arrays.copyOfRange(data, start, start + 2));
            start += 2;

            byte[] bt = Arrays.copyOfRange(data, start, start + lgt);
            String s = new String(bt);
            start += lgt;

            attributeMap.put(type,new StunAttribute(type, bt));

            start = SignalUtil.multipleOfFour(start);
        }

        return new StunMessage(header, attributeMap, true, true, null, null);
    }

    public void validate(String pass,byte[] data) {
        StunAttribute fingerprint = this.attributeSet.remove(StunAttributeTypeEnum.FINGERPRINT);
        String finger = new String(fingerprint.getData());
        byte[] fingerprintData = Arrays.copyOfRange(data,0,data.length-fingerprint.getLength()-4);
        final CRC32 crc = new CRC32();
        crc.update(fingerprintData);
        String comp = new String(SignalUtil.xor(SignalUtil.fourBytesFromInt((int) crc.getValue()),
                new byte[]{0x53, 0x54, 0x55, 0x4e}));

        if(!comp.equals(finger)) {
            throw new StunError("Fingerprint bad, computed="+comp+" sent="+finger);
        }

        StunAttribute integrity = attributeSet.remove(StunAttributeTypeEnum.MESSAGE_INTEGRITY);

        byte[] integrityData = Arrays.copyOfRange(data,0,data.length-fingerprint.getLength()-integrity.getLength()-8);
        byte[] lgt = SignalUtil.twoBytesFromInt(fingerprintData.length-20);
        integrityData[2] = lgt[0];
        integrityData[3] = lgt[1];

        byte[] mac = SignalUtil.hmacSha1(integrityData,pass.getBytes());

        if(!Arrays.equals(mac,integrity.getData())) {
            throw new StunError("Integrity bad, computed="
                    + Hex.encodeHexString(mac)+" sent="+Hex.encodeHexString(integrity.getData()));
        }
    }

    /**
     * @param header       parsed header
     * @param attributeMap attribute map
     */
    private StunMessage(StunHeader header,
                        Map<StunAttributeTypeEnum,StunAttribute> attributeMap,
                        boolean withIntegrity,
                        boolean withFingerprint,
                        String username,
                        String password) {
        this.username = username;
        this.header = header;
        this.attributeSet = attributeMap;
        this.withIntegrity = withIntegrity;
        this.withFingerprint = withFingerprint;
        this.password = password;
    }

    public StunHeader getHeader() {
        return header;
    }

    public Map<StunAttributeTypeEnum,StunAttribute> getAttributeSet() {
        return attributeSet;
    }

    /**
     * @return array of bytes representing message
     */
    public byte[] toBytes() {
        final List<byte[]> bt = new ArrayList<>();

        attributeSet.values()
                .forEach(i -> bt.add(i.toBytes()));

        StunHeader hd1 = withIntegrity ? header.updateMessageLength(header.getMessageLength() + 24) : header;
        if (withIntegrity) {
            byte[] macData = SignalUtil.joinBytesArrays(hd1.toBytes(), SignalUtil.joinBytesArrays(bt));

            byte[] mac = SignalUtil.hmacSha1(
                    macData,
                    password.getBytes());

            StunAttribute integrity = new StunAttribute(
                    StunAttributeTypeEnum.MESSAGE_INTEGRITY,
                    mac
            );
            bt.add(integrity.toBytes());
        }

        StunHeader hd2 = withFingerprint ? hd1.updateMessageLength(hd1.getMessageLength() + 8) : hd1;
        if (withFingerprint) {
            //Generate fingerprint
            final CRC32 crc = new CRC32();
            crc.update(hd2.toBytes());
            bt.forEach(crc::update);
            byte[] xorCRC32 =
                    SignalUtil.xor(SignalUtil.fourBytesFromInt((int) crc.getValue()),
                            new byte[]{0x53, 0x54, 0x55, 0x4e});

            StunAttribute finger = new StunAttribute(
                    StunAttributeTypeEnum.FINGERPRINT,
                    xorCRC32
            );
            bt.add(finger.toBytes());
        }

        return SignalUtil.joinBytesArrays(hd2.toBytes(), SignalUtil.joinBytesArrays(bt));
    }

    @Override
    public String toString() {
        return "StunMessage{" +
                "header=" + header +
                ", attributeSet=" + attributeSet +
                ", withIntegrity=" + withIntegrity +
                ", withFingerprint=" + withFingerprint +
                ", password='" + password + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}
