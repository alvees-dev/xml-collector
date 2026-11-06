package com.alveesdev.xmlcollector.infra.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.application.settings.Theme;

class PropertiesThemeRepositoryTest {

	@TempDir
	Path directory;

	@Test
	void aNewInstallationOpensWithTheDefaultTheme() {
		assertEquals(Theme.DEFAULT, new PropertiesThemeRepository(directory.resolve("appearance.properties")).load());
	}

	@Test
	void theChosenThemeComesBack() throws IOException {
		PropertiesThemeRepository repository = new PropertiesThemeRepository(directory.resolve("Config").resolve("appearance.properties"));

		repository.save(Theme.NIGHT);

		assertEquals(Theme.NIGHT, repository.load());
	}

	@Test
	void anUnknownThemeFallsBackToTheDefault() throws IOException {
		Path file = directory.resolve("appearance.properties");
		Files.writeString(file, "theme=ROSA\n");

		assertEquals(Theme.DEFAULT, new PropertiesThemeRepository(file).load());
	}
}
