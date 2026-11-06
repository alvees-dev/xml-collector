package com.alveesdev.xmlcollector.infra.sefaz;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.TrustManager;

import com.alveesdev.xmlcollector.infra.security.SystemTrust;

public final class SefazTrust {

	// The root of ICP-Brasil, which signs the servers of most Sefaz. Java does not bring it, and Windows only has it once it downloaded it,
	// which a new computer may not have done. Taken from acraiz.icpbrasil.gov.br, SHA-256 6E:0B:FF:06:9A:26:99:4C:15:DE:2C:48:88:CC:54:AF:84:88:2E:54:95:B7:FB:F6:6B:E9:CC:FF:EC:74:89:F6.
	private static final String ICP_BRASIL_ROOT = "/com/alveesdev/xmlcollector/sefaz/icp-brasil-v10.pem";

	private SefazTrust() {
	}

	public static TrustManager[] trustManagers() throws GeneralSecurityException, IOException {

		KeyStore merged = SystemTrust.roots();
		addBundledRoots(merged);

		return SystemTrust.trustManagers(merged);
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
}
