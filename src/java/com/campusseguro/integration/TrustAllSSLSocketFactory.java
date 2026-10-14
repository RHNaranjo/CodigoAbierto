package com.campusseguro.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Tomada del proyecto LDAP. Acepta cualquier certificado: solo para el
 * laboratorio, donde Wazuh y LDAP usan certificados autofirmados.
 */
public class TrustAllSSLSocketFactory extends SSLSocketFactory {

    private SSLSocketFactory factory;

    public TrustAllSSLSocketFactory() {
        factory = contexto().getSocketFactory();
    }

    // Contexto TLS que no valida certificado ni nombre del servidor.
    // Se usa también con HttpClient para la API de Wazuh.
    public static SSLContext contexto() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509ExtendedTrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket s) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket s) {}
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine e) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine e) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            return ctx;
        } catch (KeyManagementException | NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static SocketFactory getDefault() { return new TrustAllSSLSocketFactory(); }

    @Override public String[] getDefaultCipherSuites() { return factory.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return factory.getSupportedCipherSuites(); }
    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException { return factory.createSocket(s, host, port, autoClose); }
    @Override public Socket createSocket(String host, int port) throws IOException { return factory.createSocket(host, port); }
    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException { return factory.createSocket(host, port, localHost, localPort); }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException { return factory.createSocket(host, port); }
    @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException { return factory.createSocket(address, port, localAddress, localPort); }
}
