package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.AppSettings;

public interface SettingsRepository {

	AppSettings load() throws IOException;

	void save(AppSettings settings) throws IOException;
}
