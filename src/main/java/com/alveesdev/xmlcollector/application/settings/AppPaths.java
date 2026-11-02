package com.alveesdev.xmlcollector.application.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPaths {

	private static final String ROOT_DRIVE = "C:\\";
	private static final String ROOT_DIR_NAME = "Coletor XML";
	private static final String CONFIG_DIR_NAME = "Config";
	private static final String DATABASE_DIR_NAME = "Database";
	private static final String XML_ARCHIVE_DIR_NAME = "Arquivos_XML";
	private static final String EMAIL_SETTINGS_FILE_NAME = "email.properties";
	private static final String GOOGLE_CREDENTIALS_FILE_NAME = "google_credentials.json";
	private static final String GOOGLE_TOKENS_DIR_NAME = "google_tokens";

	private final Path root;

	public AppPaths(Path root) {
		this.root = root;
	}

	public static AppPaths standard() {
		return new AppPaths(Path.of(ROOT_DRIVE, ROOT_DIR_NAME));
	}

	public Path root() {
		return root;
	}

	public Path configDir() {
		return root.resolve(CONFIG_DIR_NAME);
	}

	public Path databaseDir() {
		return root.resolve(DATABASE_DIR_NAME);
	}

	public Path xmlArchiveDir() {
		return root.resolve(XML_ARCHIVE_DIR_NAME);
	}

	public Path emailSettingsFile() {
		return configDir().resolve(EMAIL_SETTINGS_FILE_NAME);
	}

	public Path googleCredentialsFile() {
		return configDir().resolve(GOOGLE_CREDENTIALS_FILE_NAME);
	}

	public Path googleTokensDir() {
		return configDir().resolve(GOOGLE_TOKENS_DIR_NAME);
	}

	public void ensureDirectories() throws IOException {
		Files.createDirectories(configDir());
		Files.createDirectories(databaseDir());
		Files.createDirectories(xmlArchiveDir());
	}
}