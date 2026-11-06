package com.alveesdev.xmlcollector.application.settings;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

// The version is written only in the pom; the build copies it into this file of the program.
public final class AppVersion {

	static final String RESOURCE = "/com/alveesdev/xmlcollector/version.properties";
	static final String UNKNOWN = "desconhecida";

	private static final String KEY = "version";

	private AppVersion() {
	}

	public static String current() {
		try (InputStream input = AppVersion.class.getResourceAsStream(RESOURCE)) {
			return input == null ? UNKNOWN : read(input);
		} catch (IOException e) {
			return UNKNOWN;
		}
	}

	static String read(InputStream input) throws IOException {
		Properties properties = new Properties();
		properties.load(input);

		String version = properties.getProperty(KEY, "").trim();

		// Run from the sources without the build, the file still has the placeholder of the pom.
		return version.isEmpty() || version.startsWith("${") ? UNKNOWN : version;
	}
}
