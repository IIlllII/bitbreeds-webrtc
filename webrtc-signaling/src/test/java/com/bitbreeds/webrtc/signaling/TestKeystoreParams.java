package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.peerconnection.ServerProperties;

/*
 * Copyright (c) 12/04/2018, Jonas Waage
 */
public class TestKeystoreParams {

    public static void initialize() {
        System.setProperty(ServerProperties.KEYSTORE, "./src/test/resources/ws2.jks");
        System.setProperty(ServerProperties.ALIAS, "websocket");
        System.setProperty(ServerProperties.PASS, "websocket");
    }
}
