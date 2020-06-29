package com.bitbreeds.webrtc.signaling;

import org.apache.camel.CamelContext;

/*
 * Copyright (c) 27/06/16, Jonas Waage
 */


/**
 *
 * Run to start a server starting lossy peerconnections.
 *
 */
public class LossyMain {

    public static void main(String[] args) throws Exception {

        CamelContext ctx = SimpleSignaling.camelContextLossy(5,5,SimpleSignaling::setupPeerConnectionDuplicateCheck);
        ctx.start();
    }

}
