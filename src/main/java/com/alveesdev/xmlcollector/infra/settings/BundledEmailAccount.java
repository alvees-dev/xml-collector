package com.alveesdev.xmlcollector.infra.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.alveesdev.xmlcollector.application.settings.DefaultEmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;

public final class BundledEmailAccount {

	private static final String RESOURCE = "/default-email.properties";
	private static final String USERNAME_KEY = "smtp.username";
	private static final String PASSWORD_KEY = "smtp.password";

	private BundledEmailAccount() {
	}

	public static EmailAccount load() {
		try (InputStream input = BundledEmailAccount.class.getResourceAsStream(RESOURCE)) {
			return input == null ? DefaultEmailAccount.get() : read(input);
		} catch (IOException | IllegalArgumentException e) {
			return DefaultEmailAccount.get();
		}
	}

	static EmailAccount read(InputStream input) throws IOException {
		Properties properties = new Properties();

		try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}

		EmailAccount defaults = DefaultEmailAccount.get();

		return new EmailAccount(
				defaults.host(),
				defaults.port(),
				defaults.security(),
				properties.getProperty(USERNAME_KEY, defaults.username()).trim(),
				properties.getProperty(PASSWORD_KEY, defaults.password()).trim());
	}
}
