package com.bitbreeds.webrtc.signaling;/*
 *
 * Copyright (c) 22/09/2018, Jonas Waage
 */

import org.apache.camel.CamelContext;

/*
 * Main method for testing long running connection
 */
public class TestLong {

    public static void main(String[] args) throws Exception {
        CamelContext ctx = SimpleSignaling.initContext(SimpleSignaling::throughputConnection);
        ctx.start();
    }

}
