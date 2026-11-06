package com.alveesdev.xmlcollector.infra.sefaz;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class SefazTrust {

	private static final String WINDOWS_ROOT_STORE = "Windows-ROOT";

	// The root of ICP-Brasil, which signs the servers of most Sefaz. Java does not bring it, and Windows only has it once it downloaded it,
	// which a new computer may not have done. Taken from acraiz.icpbrasil.gov.br, SHA-256 6E:0B:FF:06:9A:26:99:4C:15:DE:2C:48:88:CC:54:AF:84:88:2E:54:95:B7:FB:F6:6B:E9:CC:FF:EC:74:89:F6.
	private static final String ICP_BRASIL_ROOT = "/com/alveesdev/xmlcollector/sefaz/icp-brasil-v10.pem";

	private SefazTrust() {
	}

	public static TrustManager[] trustManagers() throws GeneralSecurityException, IOException {

		KeyStore merged = KeyStore.getInstance(KeyStore.getDefaultType());
		merged.load(null, null);

		addJavaRoots(merged);
		addBundledRoots(merged);
		addWindowsRoots(merged);

		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init(merged);

		return factory.getTrustManagers();
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

	public static List<X509Certificate> bundledRoots() throws GeneralSecurityException, IOException {

		try (InputStream input = SefazTrust.class.getResourceAsStream(ICP_BRASIL_ROOT)) {
			if (input == null) {
				throw new IOException("A raiz da ICP-Brasil não está no programa: " + ICP_BRASIL_ROOT);
			}

			return CertificateFactory.getInstance("X.509").generateCertificates(input).stream()
					.map(X509Certificate.class::cast)
					.toList();
		}
	}

	private static void addBundledRoots(KeyStore merged) throws GeneralSecurityException, IOException {

		int index = 0;

		for (X509Certificate root : bundledRoots()) {
			merged.setCertificateEntry("bundled-" + index++, root);
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
