package com.alveesdev.xmlcollector.infra.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

// The roots Java brings plus the roots of Windows. Windows also has the roots that an antivirus or the company network
// installs to look into the secure connections, which Java alone refuses.
public final class SystemTrust {

	private static final String WINDOWS_ROOT_STORE = "Windows-ROOT";

	private SystemTrust() {
	}

	public static KeyStore roots() throws GeneralSecurityException, IOException {

		KeyStore merged = KeyStore.getInstance(KeyStore.getDefaultType());
		merged.load(null, null);

		addJavaRoots(merged);
		addWindowsRoots(merged);

		return merged;
	}

	public static TrustManager[] trustManagers(KeyStore roots) throws GeneralSecurityException {

		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init(roots);

		return factory.getTrustManagers();
	}

	public static SSLContext context() throws GeneralSecurityException, IOException {

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, trustManagers(roots()), null);

		return context;
	}

	private static void addJavaRoots(KeyStore merged) throws GeneralSecurityException {

		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init((KeyStore) null);

		int index = 0;

		for (TrustManager manager : factory.getTrustManagers()) {
			if (manager instanceof X509TrustManager x509) {
				for (X509Certificate issuer : x509.getAcceptedIssuers()) {
					merged.setCertificateEntry("java-" + index++, issuer);
				}
			}
		}
	}

	private static void addWindowsRoots(KeyStore merged) throws GeneralSecurityException, IOException {

		KeyStore windows;

		try {
			windows = KeyStore.getInstance(WINDOWS_ROOT_STORE);
		} catch (KeyStoreException e) {
			return;
		}

		windows.load(null, null);

		int index = 0;

		for (String alias : Collections.list(windows.aliases())) {
			Certificate certificate = windows.getCertificate(alias);

			if (certificate != null) {
				merged.setCertificateEntry("windows-" + index++, certificate);
			}
		}
	}
}
