package com.alveesdev.xmlcollector.infra.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;

import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

class SefazTrustTest {

	// The fingerprint published with the file of the root on the site of ICP-Brasil.
	private static final String ICP_BRASIL_V10 = "6e0bff069a26994c15de2c4888cc54af84882e5495b7fbf66be9ccffec7489f6";

	@Test
	void theProgramBringsTheRootOfIcpBrasil() throws Exception {
		List<X509Certificate> roots = SefazTrust.bundledRoots();

		assertEquals(1, roots.size());
		X509Certificate root = roots.get(0);
		assertEquals(ICP_BRASIL_V10, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root.getEncoded())));
		assertEquals(root.getSubjectX500Principal(), root.getIssuerX500Principal());
		assertTrue(root.getSubjectX500Principal().getName().contains("Raiz Brasileira v10"));
	}

	@Test
	void theConnectionToTheSefazTrustsTheRootItBrings() throws Exception {
		X509Certificate root = SefazTrust.bundledRoots().get(0);
		X509TrustManager manager = (X509TrustManager) SefazTrust.trustManagers()[0];

		assertTrue(List.of(manager.getAcceptedIssuers()).contains(root));
	}
}
