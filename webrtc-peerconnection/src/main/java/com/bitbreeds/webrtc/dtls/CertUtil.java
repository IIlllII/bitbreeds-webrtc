package com.bitbreeds.webrtc.dtls;

import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.x509.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;

/*
 * Copyright (c) 02/03/2017, Jonas Waage
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
 * Convenience methods related to certificates
 */
public class CertUtil {

    private final static Logger logger = LoggerFactory.getLogger(CertUtil.class);

    /**
     * @param alias alias
     * @param pass password
     * @param storePath path to keystore
     * @return sha-256 string based on cert in keystore
     */
    public static String getCertFingerPrint(String storePath, String alias,String pass) {
        try {
            Certificate cert = DTLSUtils.loadCert(storePath,
                    alias,
                    pass);

            byte[] der = cert.getEncoded();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dat = md.digest(der);

            String fingerprint = createFingerprintString(dat);
            logger.info("Local cert signature is {} ",fingerprint);
            return fingerprint;
        } catch (Exception e) {
            logger.error("Failed to create cert fingerprint from {}",storePath ,e);
            throw new IllegalStateException("Loading certificate failed");
        }
    }

    /**
     *
     * @param fingerPrint sha256 of an encoded cert
     * @return String description of fingerprint formatted 'sha-256 AB:CD...'
     */
    public static String createFingerprintString(byte[] fingerPrint) {
        StringBuilder bldr = new StringBuilder();
        bldr.append("sha-256 ");
        for(int i=0;i<fingerPrint.length;i++) {

            String a = Hex.encodeHexString(new byte[] {fingerPrint[i]});
            bldr.append(a.toUpperCase());
            if(i != fingerPrint.length-1) {
                bldr.append(":");
            }
        }
        return bldr.toString();
    }



}
