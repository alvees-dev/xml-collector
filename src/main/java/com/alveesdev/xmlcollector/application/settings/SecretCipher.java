package com.alveesdev.xmlcollector.application.settings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class SecretCipher {

	private static final String PREFIX = "enc:";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final String APPLICATION_SALT = "coletor-xml-email-password";
	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;

	private static final SecureRandom RANDOM = new SecureRandom();

	private SecretCipher() {
	}

	static String encrypt(String plainText) {
		if (plainText == null || plainText.isEmpty()) {
			return "";
		}

		try {
			byte[] iv = new byte[IV_LENGTH];
			RANDOM.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			byte[] payload = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, payload, 0, iv.length);
			System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

			return PREFIX + Base64.getEncoder().encodeToString(payload);

		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Password could not be encrypted", e);
		}
	}

	static Optional<String> decrypt(String stored) {
		if (stored == null || !stored.startsWith(PREFIX)) {
			return Optional.empty();
		}

		try {
			byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));

			if (payload.length <= IV_LENGTH) {
				return Optional.empty();
			}

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(payload, 0, IV_LENGTH)));
			byte[] decrypted = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);

			return Optional.of(new String(decrypted, StandardCharsets.UTF_8));

		} catch (GeneralSecurityException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static SecretKeySpec key() throws GeneralSecurityException {
		String material = computerName() + "|" + System.getProperty("user.name", "") + "|" + APPLICATION_SALT;
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));

		return new SecretKeySpec(hash, "AES");
	}

	private static String computerName() {
		String fromEnvironment = System.getenv("COMPUTERNAME");

		if (fromEnvironment != null && !fromEnvironment.isBlank()) {
			return fromEnvironment;
		}

		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "localhost";
		}
	}
}