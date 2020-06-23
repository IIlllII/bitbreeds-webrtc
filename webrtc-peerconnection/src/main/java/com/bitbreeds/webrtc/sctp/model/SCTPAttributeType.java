package com.bitbreeds.webrtc.sctp.model;

import com.bitbreeds.webrtc.common.SignalUtil;

import java.util.Arrays;

/*
 * Copyright (c) 18/05/16, Jonas Waage
 */

/**
 * Representation of variable length paramaters in SCTP messages
 *
 * Described in <a href=http://www.iana.org/assignments/sctp-parameters/sctp-parameters.xhtml>SCTP messages and params</a>
 */
public enum SCTPAttributeType {

    NOT_KNOWN(-1),
    HERTBEAT_INFO(1),
    IPV4(5),
    IPV6(6),
    STATE_COOKIE(7),
    COOKIE_PRESERVATIVE(9),
    HOST_NAME(11),
    ADDRESS_TYPES(12),
    OUTGOING_SSN_RESET_REQUEST(13),
    INCOMING_SSN_RESET_REQUEST(14),
    SSN_TSN_RESET_REQEUST(15),
    RE_CONFIG(16),
    ADD_OUTGOING_STREAMS(17),
    ADD_INCOMING_STREAMS(18),
    FORWARD_TSN(0xC000),
    RANDOM(0x8002),
    CHUNK_LIST(0x8003),
    HMAC_ALGORITHM(0x8004),
    PADDING(0x8005),
    SUPPORTED_EXTENSIONS(0x8008),
    ADD_IP(0xC001),
    DELETE_IP(0xC002),
    ERROR_CAUSE(0xC003),
    SET_PRIMARY_ADDRESS(0xC004),
    SUCCESS_INDICATION(0xC005),
    ADAPTATION_LAYER_INDICATION(0xC006);

    private final int nr;

    SCTPAttributeType(int nr) {
        this.nr = nr;
    }

    public int getNr() {
        return nr;
    }

    public byte[] toBytes() {
        return SignalUtil.twoBytesFromInt(nr);
    }

    public static SCTPAttributeType fromInt(int bt) {
        return Arrays.asList(values())
                .stream()
                .filter(i -> i.nr == bt)
                .findFirst().orElse(
                     NOT_KNOWN
                );
    }

}
