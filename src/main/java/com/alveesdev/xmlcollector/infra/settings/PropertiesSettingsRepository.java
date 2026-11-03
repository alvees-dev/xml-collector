package com.alveesdev.xmlcollector.infra.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.alveesdev.xmlcollector.application.port.SettingsRepository;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.PdvType;

public final class PropertiesSettingsRepository implements SettingsRepository {

	private static final String PDV_DIRECTORIES_PREFIX = "directories.pdv.";
	private static final String NFE_DIRECTORIES_PREFIX = "directories.nfe.";
	private static final String LEGACY_NFE_SALES_DIRECTORIES_PREFIX = "directories.nfe.sales.";
	private static final String PDV_TYPE_KEY = "search.pdvType";
	private static final String PDV_MODEL_KEY = "search.pdvModel";
	private static final String NFE_MODEL_KEY = "search.nfeModel";
	private static final String ORGANIZE_MODE_KEY = "archive.organizeMode";
	private static final String HOST_KEY = "smtp.host";
	private static final String PORT_KEY = "smtp.port";
	private static final String SECURITY_KEY = "smtp.security";
	private static final String USERNAME_KEY = "smtp.username";
	private static final String PASSWORD_KEY = "smtp.password";
	private static final String RECIPIENT_KEY = "message.recipient";
	private static final String SUBJECT_KEY = "message.subject";
	private static final String BODY_KEY = "message.body";

	private final Path file;
	private final Path legacyEmailFile;
	private final EmailAccount defaultAccount;

	public PropertiesSettingsRepository(Path file, Path legacyEmailFile, EmailAccount defaultAccount) {
		this.file = file;
		this.legacyEmailFile = legacyEmailFile;
		this.defaultAccount = defaultAccount;
	}

	@Override
	public AppSettings load() throws IOException {
		AppSettings defaults = AppSettings.defaults().withEmailAccount(defaultAccount);
		Path source = Files.isRegularFile(file) ? file : legacyEmailFile;

		if (!Files.isRegularFile(source)) {
			return defaults;
		}

		Properties properties = read(source);

		return new AppSettings(
				readDirectories(properties, PDV_DIRECTORIES_PREFIX, defaults.pdvDirectories()),
				readDirectories(properties, NFE_DIRECTORIES_PREFIX,
						readDirectories(properties, LEGACY_NFE_SALES_DIRECTORIES_PREFIX, defaults.nfeDirectories())),
				parsePdvType(properties.getProperty(PDV_TYPE_KEY), defaults.pdvType()),
				properties.getProperty(PDV_MODEL_KEY, defaults.pdvModelId()),
				properties.getProperty(NFE_MODEL_KEY, defaults.nfeModelId()),
				parseOrganizeMode(properties.getProperty(ORGANIZE_MODE_KEY), defaults.organizeMode()),
				readEmailAccount(properties),
				new EmailDraft(
						properties.getProperty(RECIPIENT_KEY, ""),
						properties.getProperty(SUBJECT_KEY, ""),
						properties.getProperty(BODY_KEY, "")));
	}

	@Override
	public void save(AppSettings settings) throws IOException {
		EmailAccount account = settings.emailAccount();
		EmailDraft draft = settings.emailDraft();

		Properties properties = new Properties();
		writeDirectories(properties, PDV_DIRECTORIES_PREFIX, settings.pdvDirectories());
		writeDirectories(properties, NFE_DIRECTORIES_PREFIX, settings.nfeDirectories());
		properties.setProperty(PDV_TYPE_KEY, settings.pdvType().name());
		properties.setProperty(PDV_MODEL_KEY, settings.pdvModelId());
		properties.setProperty(NFE_MODEL_KEY, settings.nfeModelId());
		properties.setProperty(ORGANIZE_MODE_KEY, settings.organizeMode().name());

		if (!account.equals(defaultAccount)) {
			properties.setProperty(HOST_KEY, account.host());
			properties.setProperty(PORT_KEY, String.valueOf(account.port()));
			properties.setProperty(SECURITY_KEY, account.security().name());
			properties.setProperty(USERNAME_KEY, account.username());
			properties.setProperty(PASSWORD_KEY, SecretCipher.encrypt(account.password()));
		}

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

		Files.deleteIfExists(legacyEmailFile);
	}

	private static Properties read(Path source) throws IOException {
		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IllegalArgumentException e) {
			throw new IOException("Arquivo de configurações inválido: " + source, e);
		}

		return properties;
	}

	private static List<String> readDirectories(Properties properties, String prefix, List<String> fallback) {
		List<String> directories = new ArrayList<>();

		for (int index = 0; properties.containsKey(prefix + index); index++) {
			directories.add(properties.getProperty(prefix + index));
		}

		return directories.isEmpty() ? fallback : directories;
	}

	private static void writeDirectories(Properties properties, String prefix, List<String> directories) {
		for (int index = 0; index < directories.size(); index++) {
			properties.setProperty(prefix + index, directories.get(index));
		}
	}

	private EmailAccount readEmailAccount(Properties properties) {
		String storedPassword = properties.getProperty(PASSWORD_KEY);
		String password = storedPassword == null
				? defaultAccount.password()
				: SecretCipher.decrypt(storedPassword).orElse("");

		EmailAccount account = new EmailAccount(
				properties.getProperty(HOST_KEY, defaultAccount.host()),
				parsePort(properties.getProperty(PORT_KEY), defaultAccount.port()),
				parseSecurity(properties.getProperty(SECURITY_KEY), defaultAccount.security()),
				properties.getProperty(USERNAME_KEY, defaultAccount.username()),
				password);

		return account.isComplete() || !defaultAccount.isComplete() ? account : defaultAccount;
	}

	private static OrganizeMode parseOrganizeMode(String text, OrganizeMode fallback) {
		try {
			return text == null ? fallback : OrganizeMode.valueOf(text.trim());
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	private static PdvType parsePdvType(String text, PdvType fallback) {
		try {
			return text == null ? fallback : PdvType.valueOf(text.trim());
		} catch (IllegalArgumentException e) {
			return fallback;
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
