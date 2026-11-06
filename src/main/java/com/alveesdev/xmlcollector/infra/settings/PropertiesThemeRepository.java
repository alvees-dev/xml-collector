package com.alveesdev.xmlcollector.infra.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.alveesdev.xmlcollector.application.port.ThemeRepository;
import com.alveesdev.xmlcollector.application.settings.Theme;

public final class PropertiesThemeRepository implements ThemeRepository {

	private static final String THEME_KEY = "theme";

	private final Path file;

	public PropertiesThemeRepository(Path file) {
		this.file = file;
	}

	// A missing or unreadable file only means the program opens with the default colors.
	@Override
	public Theme load() {
		if (!Files.isRegularFile(file)) {
			return Theme.DEFAULT;
		}

		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
			return Theme.valueOf(properties.getProperty(THEME_KEY, Theme.DEFAULT.name()).trim());
		} catch (IOException | IllegalArgumentException e) {
			return Theme.DEFAULT;
		}
	}

	@Override
	public void save(Theme theme) throws IOException {
		Properties properties = new Properties();
		properties.setProperty(THEME_KEY, theme.name());

		Files.createDirectories(file.getParent());

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			properties.store(writer, null);
		}
	}
}
