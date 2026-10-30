package com.alveesdev.xmlcollector.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPaths {

	private static final String ROOT_DRIVE = "C:\\";
	private static final String ROOT_DIR_NAME = "Coletor XML";
	private static final String CONFIG_DIR_NAME = "Config";
	private static final String DATABASE_DIR_NAME = "Database";
	private static final String XML_ARCHIVE_DIR_NAME = "Arquivos_XML";
	private static final String VOIDED_DIR_NAME = "Inutilizadas";

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

	public Path voidedDir() {
		return xmlArchiveDir().resolve(VOIDED_DIR_NAME);
	}

	public void ensureDirectories() throws IOException {
		Files.createDirectories(configDir());
		Files.createDirectories(databaseDir());
		Files.createDirectories(xmlArchiveDir());
		Files.createDirectories(voidedDir());
	}
}