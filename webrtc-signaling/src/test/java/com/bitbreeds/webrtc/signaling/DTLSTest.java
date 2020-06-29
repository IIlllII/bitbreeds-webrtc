package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.dtls.DTLSUtils;
import org.junit.Test;

/*
 * Copyright (c) 16/05/16, Jonas Waage
 */
public class DTLSTest {


    @Test
    public void loadDTLSCert() {
        DTLSUtils.loadCert("./src/test/resources/ws2.jks",
                "websocket",
                "websocket");
    }

}
