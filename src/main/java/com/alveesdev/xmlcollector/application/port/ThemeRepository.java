package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.Theme;

// Kept apart from the settings, so choosing a theme saves at once and never touches the rest.
public interface ThemeRepository {

	Theme load();

	void save(Theme theme) throws IOException;
}
