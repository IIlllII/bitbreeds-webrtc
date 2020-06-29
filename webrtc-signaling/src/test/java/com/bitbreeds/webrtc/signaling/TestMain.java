package com.bitbreeds.webrtc.signaling;/*
 *
 * Copyright (c) 22/09/2018, Jonas Waage
 */

import org.apache.camel.CamelContext;

public class TestMain {

    public static void main(String[] args) throws Exception {
        CamelContext ctx = SimpleSignaling.camelContextLossy(5, 5,SimpleSignaling::setupPeerConnectionDuplicateCheck);
        ctx.start();
    }

}
