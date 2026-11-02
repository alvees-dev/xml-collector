package com.alveesdev.xmlcollector.application.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public final class EmailSettingsStore {

	private static final String HOST_KEY = "smtp.host";
	private static final String PORT_KEY = "smtp.port";
	private static final String SECURITY_KEY = "smtp.security";
	private static final String USERNAME_KEY = "smtp.username";
	private static final String PASSWORD_KEY = "smtp.password";
	private static final String RECIPIENT_KEY = "message.recipient";
	private static final String SUBJECT_KEY = "message.subject";
	private static final String BODY_KEY = "message.body";

	private final Path file;

	public EmailSettingsStore(Path file) {
		this.file = file;
	}

	public void load(AppSettings settings) throws IOException {
		if (!Files.isRegularFile(file)) {
			return;
		}

		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}

		EmailAccount defaults = DefaultEmailAccount.get();

		String storedPassword = properties.getProperty(PASSWORD_KEY);
		String password = storedPassword == null
				? defaults.password()
				: SecretCipher.decrypt(storedPassword).orElse("");

		settings.setEmailAccount(new EmailAccount(
				properties.getProperty(HOST_KEY, defaults.host()),
				parsePort(properties.getProperty(PORT_KEY), defaults.port()),
				parseSecurity(properties.getProperty(SECURITY_KEY), defaults.security()),
				properties.getProperty(USERNAME_KEY, defaults.username()),
				password));

		settings.setEmailDraft(new EmailDraft(
				properties.getProperty(RECIPIENT_KEY, ""),
				properties.getProperty(SUBJECT_KEY, ""),
				properties.getProperty(BODY_KEY, "")));
	}

	public void save(AppSettings settings) throws IOException {
		EmailAccount account = settings.getEmailAccount();
		EmailDraft draft = settings.getEmailDraft();

		Properties properties = new Properties();
		properties.setProperty(HOST_KEY, account.host());
		properties.setProperty(PORT_KEY, String.valueOf(account.port()));
		properties.setProperty(SECURITY_KEY, account.security().name());
		properties.setProperty(USERNAME_KEY, account.username());
		properties.setProperty(PASSWORD_KEY, SecretCipher.encrypt(account.password()));
		properties.setProperty(RECIPIENT_KEY, draft.recipient());
		properties.setProperty(SUBJECT_KEY, draft.subject());
		properties.setProperty(BODY_KEY, draft.body());

		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".part");

		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			properties.store(writer, null);
		}

		try {
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static int parsePort(String text, int fallback) {
		try {
			return text == null ? fallback : Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static EmailSecurity parseSecurity(String text, EmailSecurity fallback) {
		try {
			return text == null ? fallback : EmailSecurity.valueOf(text.trim());
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}