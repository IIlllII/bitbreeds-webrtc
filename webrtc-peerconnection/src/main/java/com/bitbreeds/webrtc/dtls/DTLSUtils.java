package com.bitbreeds.webrtc.dtls;

/**
 * Copyright (c) 16/05/16, Jonas Waage
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
import javafx.util.Pair;
import org.bouncycastle.crypto.RuntimeCryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;


/**
 * Allow loading of our certificates from keystore
 */
public class DTLSUtils {

    private final static Logger logger = LoggerFactory.getLogger(DTLSUtils.class);

    public static org.bouncycastle.crypto.tls.Certificate loadCert(String keystore,String alias,String password) {
        try {
            byte[] data = getCert(keystore,alias,password).getKey().getEncoded();
            org.bouncycastle.asn1.x509.Certificate[] cert = {org.bouncycastle.asn1.x509.Certificate.getInstance(data)};
            return new org.bouncycastle.crypto.tls.Certificate(cert);
        } catch (Exception e) {
            logger.error("Error loading certificate: ",e);
            throw new RuntimeCryptoException("Problem loading certificate");
        }
    }

    public static Pair<Certificate,KeyPair> getCert(String keystore, String alias, String password) {
        logger.info("Loading cert from {} with alias {}",keystore,alias);
        KeyStore ks  = null;
        try {
            ks = KeyStore.getInstance("JKS");
            File fl = new File(keystore);
            FileInputStream stream = new FileInputStream(fl);
            ks.load(stream, password.toCharArray());
            final Key key = ks.getKey(alias, password.toCharArray());
            Certificate cert = ks.getCertificate(alias);
            KeyPair kp = new KeyPair(cert.getPublicKey(), (PrivateKey) key);
            return new Pair<>(ks.getCertificate(alias), kp);
        } catch (Exception e) {
            logger.error("Error loading certificate: ",e);
            throw new RuntimeCryptoException("Problem loading certificate");
        }
    }



}
