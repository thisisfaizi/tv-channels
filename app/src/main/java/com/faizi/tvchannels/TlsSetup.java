package com.faizi.tvchannels;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * KitKat (API 19) ships an outdated CA store and disables TLS 1.2 by default.
 * This installs a trust store built from bundled modern root CAs (res/raw/roots.pem)
 * and forces TLS 1.1/1.2 on every socket, so HTTPS CDNs (Let's Encrypt, DigiCert,
 * Sectigo, Akamai) work WITHOUT disabling certificate validation.
 */
public final class TlsSetup {

    public static void install(Context ctx) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            InputStream in = ctx.getResources().openRawResource(R.raw.roots);
            Collection<? extends Certificate> certs = cf.generateCertificates(in);
            in.close();
            int i = 0;
            for (Certificate c : certs) {
                ks.setCertificateEntry("ca" + (i++), c);
            }
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, tmf.getTrustManagers(), null);
            HttpsURLConnection.setDefaultSSLSocketFactory(
                    new Tls12SocketFactory(sc.getSocketFactory()));
        } catch (Exception e) {
            // Fall back to platform default; some HTTPS streams may fail.
        }
    }

    private TlsSetup() {}
}

/** Wraps a socket factory so every SSL socket enables TLS 1.1/1.2. */
class Tls12SocketFactory extends SSLSocketFactory {
    private static final String[] PROTOCOLS = {"TLSv1.2", "TLSv1.1", "TLSv1"};
    private final SSLSocketFactory delegate;

    Tls12SocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    private Socket enable(Socket s) {
        if (s instanceof SSLSocket) {
            try {
                ((SSLSocket) s).setEnabledProtocols(PROTOCOLS);
            } catch (Exception ignored) {
            }
        }
        return s;
    }

    @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

    @Override public Socket createSocket() throws IOException {
        return enable(delegate.createSocket());
    }
    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return enable(delegate.createSocket(s, host, port, autoClose));
    }
    @Override public Socket createSocket(String host, int port) throws IOException {
        return enable(delegate.createSocket(host, port));
    }
    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return enable(delegate.createSocket(host, port, localHost, localPort));
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return enable(delegate.createSocket(host, port));
    }
    @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return enable(delegate.createSocket(address, port, localAddress, localPort));
    }
}
