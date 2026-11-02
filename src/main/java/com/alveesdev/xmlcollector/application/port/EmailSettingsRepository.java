package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.AppSettings;

public interface EmailSettingsRepository {

	void load(AppSettings settings) throws IOException;

	void save(AppSettings settings) throws IOException;
}
