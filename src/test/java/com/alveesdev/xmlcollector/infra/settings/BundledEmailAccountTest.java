package com.alveesdev.xmlcollector.infra.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.settings.DefaultEmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;

class BundledEmailAccountTest {

	@Test
	void readsTheAccountAndKeepsTheStandardServer() throws IOException {
		EmailAccount account = read("smtp.username = envio@exemplo.com \nsmtp.password=abcdefghijklmnop\n");
		EmailAccount defaults = DefaultEmailAccount.get();

		assertEquals("envio@exemplo.com", account.username());
		assertEquals("abcdefghijklmnop", account.password());
		assertEquals(defaults.host(), account.host());
		assertEquals(defaults.port(), account.port());
		assertEquals(defaults.security(), account.security());
		assertTrue(account.isComplete());
	}

	@Test
	void aBlankFileGivesAnIncompleteAccount() throws IOException {
		assertFalse(read("smtp.username=\nsmtp.password=\n").isComplete());
		assertFalse(read("").isComplete());
	}

	private static EmailAccount read(String content) throws IOException {
		return BundledEmailAccount.read(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
	}
}
