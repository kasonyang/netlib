package site.kason.netlib.util;

import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLContextUtil {

    public static final String SSL = "SSL",
            SSL_2 = "SSLv2",
            SSL_3 = "SSLv3",
            TLS = "TLS",
            TLS_1 = "TLSv1",
            TLS_1_1 = "TLSv1.1",
            TLS_1_2 = "TLSv1.2";

    public static SSLContext createFromKeyStore(File file, String pwd) throws KeyManagementException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        return SSLContextUtil.createFromKeyStore(file, pwd, TLS_1);
    }

    public static SSLContext createFromKeyStore(File file, String pwd, String sslProtocol) throws KeyManagementException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        FileInputStream fis = new FileInputStream(file);
        try {
            return SSLContextUtil.createFromKeyStore(fis, pwd, sslProtocol);
        } finally {
            fis.close();
        }
    }

    public static SSLContext createFromKeyStore(InputStream is, String pwd, String sslProtocol) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException {
        KeyStore ks = createKeyStore(is, pwd);
        KeyManagerFactory kmf = createKeyManager(ks, pwd);
        TrustManagerFactory tmf = createTrustManagerFactory(ks);
        return create(kmf.getKeyManagers(), tmf.getTrustManagers(), sslProtocol);
    }

    public static SSLContext create(KeyManager[] keyManagers, TrustManager[] trustManagers, String sslProtocol) throws KeyManagementException {
        SSLContext sslCtx;
        try {
            sslCtx = SSLContext.getInstance(sslProtocol);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        sslCtx.init(keyManagers, trustManagers, null);
        return sslCtx;
    }

    private static KeyManagerFactory createKeyManager(KeyStore ks, String pwd) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, pwd.toCharArray());
        return kmf;
    }

    private static TrustManagerFactory createTrustManagerFactory(KeyStore ks) throws KeyStoreException, NoSuchAlgorithmException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);
        return tmf;
    }

    private static KeyStore createKeyStore(InputStream inputStream, String pwd) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException {
        KeyStore ks = KeyStore.getInstance("JKS");
        char[] passphrase = pwd.toCharArray();
        ks.load(inputStream, passphrase);
        return ks;
    }
}
