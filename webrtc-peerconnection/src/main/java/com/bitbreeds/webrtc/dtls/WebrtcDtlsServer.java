package com.bitbreeds.webrtc.dtls;

/*
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

import com.bitbreeds.webrtc.model.webrtc.ConnectionInternalApi;
import com.bitbreeds.webrtc.peerconnection.PeerDescription;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Vector;

import static com.bitbreeds.webrtc.dtls.CertUtil.createFingerprintString;


/**
 *
 */
public class WebrtcDtlsServer
        extends DefaultTlsServer {

    private org.bouncycastle.asn1.x509.Certificate certLoaded;

    private CertKeyPair pair;

    private final Logger logger = LoggerFactory.getLogger(WebrtcDtlsServer.class);

    private final PeerDescription remote;
    private final ConnectionInternalApi peerConnection;

    public WebrtcDtlsServer(ConnectionInternalApi peerConnection, KeyStoreInfo keyStoreInfo, PeerDescription remote) throws IOException {
        super(new BcTlsCrypto(new SecureRandom()));

        this.peerConnection = peerConnection;
        this.remote = remote;

        certLoaded = DTLSUtils.loadCert(keyStoreInfo.getFilePath(),
                keyStoreInfo.getAlias(),
                keyStoreInfo.getPassword());

        pair = DTLSUtils.getCert(keyStoreInfo.getFilePath(),
                keyStoreInfo.getAlias(),
                keyStoreInfo.getPassword());

    }

    @Override
    public ProtocolVersion[] getProtocolVersions()
    {
        return new ProtocolVersion[]{ProtocolVersion.DTLSv12};
    }


    @Override
    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause) {
        logger.warn("DTLS server raised alert: " + AlertLevel.getText(alertLevel)
                + ", " + AlertDescription.getText(alertDescription));

        logger.warn("Level {} msg {} ",AlertLevel.getText(alertLevel),AlertDescription.getText(alertDescription));

        if (message != null) {
            logger.warn(message);
        }
        if (cause != null) {
            logger.error("Cause ",cause);
        }

        //Close connection, no point in continuing
        peerConnection.closeConnection();
    }

    @Override
    public void notifyAlertReceived(short alertLevel, short alertDescription) {
        logger.warn("DTLS server received alert: " + AlertLevel.getText(alertLevel)
                + ", " + AlertDescription.getText(alertDescription));
        //Close connection, no point in continuing
        peerConnection.closeConnection();
    }

    @Override
    public int[] getCipherSuites() {
        return Arrays.concatenate(super.getCipherSuites(),
                new int[]
                        {
                                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
                        });
    }

    @Override
    public CertificateRequest getCertificateRequest() {
        short[] certificateTypes = new short[]{ClientCertificateType.rsa_sign,
                ClientCertificateType.dss_sign, ClientCertificateType.ecdsa_sign};

        Vector serverSigAlgs = null;
        if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(ProtocolVersion.DTLSv12)) {
            serverSigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(this.context);
        }

        Vector<X500Name> certificateAuthorities = new Vector<>();
        certificateAuthorities.addElement(
                certLoaded.getSubject()
        );

        return new CertificateRequest(certificateTypes, serverSigAlgs, certificateAuthorities);
    }

    @Override
    public void notifyClientCertificate(org.bouncycastle.tls.Certificate clientCertificate)
            throws IOException {
        TlsCertificate[] chain = clientCertificate.getCertificateList();
        logger.info("DTLS server received client certificate chain of length: " + chain.length);

        boolean hasHit = false;

        for (int i = 0; i != chain.length; i++) {
            TlsCertificate entry = chain[i];

            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
                byte[] dat = md.digest(entry.getEncoded());

                String fingerprint = createFingerprintString(dat);

                logger.info("Received cert fingerprint - {}", fingerprint);
                logger.info("Offer fingerprint - {}", remote.getSignature());
                if (remote.getSignature().equalsIgnoreCase(fingerprint)) {
                    hasHit = true;
                }
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Missing SHA-256 hash implementation");
            }
        }

        if(!hasHit) {
            throw new IllegalStateException("Bad cert signature, not found in chain");
        }
    }

    @Override
    protected TlsCredentialedDecryptor getRSAEncryptionCredentials() throws IOException {

        BcTlsCertificate cnv = new BcTlsCertificate((BcTlsCrypto) this.context.getCrypto(),certLoaded.getEncoded());
        Certificate cert = new Certificate(new BcTlsCertificate[]{cnv});

        return new BcDefaultTlsCredentialedDecryptor(
                (BcTlsCrypto)this.context.getCrypto(),
                cert,
                new AsymmetricKeyParameter(true));
    }

    @Override
    protected TlsCredentialedSigner getRSASignerCredentials() throws IOException {

        BcTlsCertificate cnv = new BcTlsCertificate((BcTlsCrypto) this.context.getCrypto(),certLoaded.getEncoded());
        Certificate cert = new Certificate(new BcTlsCertificate[]{cnv});

        RSAPrivateCrtKey rsa = (RSAPrivateCrtKey) pair.getKeyPair().getPrivate();
        AsymmetricKeyParameter keyParam = new RSAPrivateCrtKeyParameters(rsa.getModulus(), rsa.getPublicExponent(),
                rsa.getPrivateExponent(), rsa.getPrimeP(), rsa.getPrimeQ(), rsa.getPrimeExponentP(),
                rsa.getPrimeExponentQ(), rsa.getCrtCoefficient());

        return new BcDefaultTlsCredentialedSigner(
                new TlsCryptoParameters(context),
                (BcTlsCrypto)context.getCrypto(),
                keyParam,
                cert,
                new SignatureAndHashAlgorithm(HashAlgorithm.sha256,SignatureAlgorithm.rsa));
    }

}