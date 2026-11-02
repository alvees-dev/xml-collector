package com.alveesdev.xmlcollector.application.settings.service;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.port.SettingsRepository;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;

public class SettingsService {

	private final SettingsRepository repository;

	private AppSettings current = AppSettings.defaults();

	public SettingsService(SettingsRepository repository) {
		this.repository = repository;
	}

	public void load() throws IOException {
		current = repository.load();
	}

	public AppSettings current() {
		return current;
	}

	public void updateDraft(EmailDraft draft) {
		current = current.withEmailDraft(draft);
	}

	public void save(AppSettings settings) throws IOException {
		current = settings;
		repository.save(settings);
	}
}
