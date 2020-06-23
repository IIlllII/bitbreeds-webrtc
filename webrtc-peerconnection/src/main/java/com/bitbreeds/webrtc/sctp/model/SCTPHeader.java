package com.bitbreeds.webrtc.sctp.model;

import com.bitbreeds.webrtc.common.SignalUtil;
import org.apache.commons.codec.binary.Hex;

import java.util.Arrays;

/*
 * Copyright (c) 17/05/16, Jonas Waage
 */

/**
 * Immutable SCTP common header
 * Described in <a href=https://tools.ietf.org/html/rfc4960#page-15>scpt common header</a>
 */
public class SCTPHeader {

    private final int sourcePort;
    private final int destinationPort;
    private final long verificationTag;
    private final long checksum;

    public SCTPHeader(
            int sourcePort,
            int destinationPort,
            long verificationTag,
            long checksum) {
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.verificationTag = verificationTag;
        this.checksum = checksum;
    }

    public byte[] toBytes() {
        return SignalUtil.joinBytesArrays(
                SignalUtil.twoBytesFromInt(sourcePort),
                SignalUtil.twoBytesFromInt(sourcePort),
                SignalUtil.longToFourBytes(verificationTag),
                SignalUtil.flipBytes(SignalUtil.longToFourBytes(checksum)));
    }

    public static SCTPHeader fromBytes(byte[] bytes) {
        if(bytes.length != 12) {
            throw new IllegalArgumentException("Bytes given are incorrect length to be an SCTP header: "
                    + " length: " + bytes.length + "  data:" + Hex.encodeHexString(bytes));
        }
        return new SCTPHeader(
                SignalUtil.intFromTwoBytes(Arrays.copyOf(bytes,2)),
                SignalUtil.intFromTwoBytes(Arrays.copyOfRange(bytes,2,4)),
                SignalUtil.bytesToLong(Arrays.copyOfRange(bytes,4,8)),
                SignalUtil.bytesToLong(Arrays.copyOfRange(bytes,8,12))
        );
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public long getVerificationTag() {
        return verificationTag;
    }

    public long getChecksum() {
        return checksum;
    }

    @Override
    public String toString() {
        return "SCTPHeader{" +
                "sourcePort=" + sourcePort +
                ", destinationPort=" + destinationPort +
                ", verificationTag=" + verificationTag +
                ", checksum=" + checksum +
                '}';
    }
}
