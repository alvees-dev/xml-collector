package com.alveesdev.xmlcollector.infra.sefaz;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;

public final class A1Certificate {

	private static final String KEY_STORE_TYPE = "PKCS12";

	private final KeyStore keyStore;
	private final char[] password;
	private final PrivateKey privateKey;
	private final X509Certificate certificate;

	private A1Certificate(KeyStore keyStore, char[] password, PrivateKey privateKey, X509Certificate certificate) {
		this.keyStore = keyStore;
		this.password = password;
		this.privateKey = privateKey;
		this.certificate = certificate;
	}

	public static A1Certificate load(Path file, char[] password) throws IOException, GeneralSecurityException {

		KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);

		try (InputStream input = Files.newInputStream(file)) {
			keyStore.load(input, password);
		}

		for (String alias : Collections.list(keyStore.aliases())) {
			if (keyStore.isKeyEntry(alias)) {
				return new A1Certificate(
						keyStore,
						password.clone(),
						(PrivateKey) keyStore.getKey(alias, password),
						(X509Certificate) keyStore.getCertificate(alias));
			}
		}

		throw new GeneralSecurityException("O arquivo não contém uma chave privada.");
	}

	KeyStore keyStore() {
		return keyStore;
	}

	char[] password() {
		return password;
	}

	PrivateKey privateKey() {
		return privateKey;
	}

	X509Certificate certificate() {
		return certificate;
	}

	boolean isExpired(Instant now) {
		return now.isAfter(certificate.getNotAfter().toInstant());
	}
}
