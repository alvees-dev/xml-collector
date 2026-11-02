package com.alveesdev.xmlcollector.bootstrap;

import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.dispatch.DispatchService;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.EmailSettingsRepository;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.infra.archive.MonthZipArchiver;
import com.alveesdev.xmlcollector.infra.archive.XmlArchive;
import com.alveesdev.xmlcollector.infra.drive.GoogleDriveStorage;
import com.alveesdev.xmlcollector.infra.email.SmtpEmailSender;
import com.alveesdev.xmlcollector.infra.settings.PropertiesEmailSettingsRepository;
import com.alveesdev.xmlcollector.infra.xml.NfceXmlReader;
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner;
import com.alveesdev.xmlcollector.ui.controller.MainScreenController;
import com.alveesdev.xmlcollector.ui.controller.SettingsController;
import com.alveesdev.xmlcollector.ui.view.SettingsWindow;

public final class AppContext {

	private final AppPaths paths;
	private final CollectionService collectionService;
	private final DispatchService dispatchService;
	private final EmailSettingsRepository emailSettings;
	private final SettingsWindow settingsWindow;

	public AppContext(AppPaths paths) {
		this.paths = paths;

		DocumentReader reader = new NfceXmlReader();

		this.collectionService = new CollectionService(new XmlFileScanner(), reader, new XmlArchive(reader));
		this.dispatchService = new DispatchService(
				new MonthZipArchiver(),
				new SmtpEmailSender(),
				new GoogleDriveStorage(paths.googleCredentialsFile(), paths.googleTokensDir()));
		this.emailSettings = new PropertiesEmailSettingsRepository(paths.emailSettingsFile());
		this.settingsWindow = new SettingsWindow(this::createController);
	}

	public AppPaths paths() {
		return paths;
	}

	public Object createController(Class<?> type) {
		if (type == MainScreenController.class) {
			return new MainScreenController(collectionService, dispatchService, emailSettings, paths, settingsWindow);
		}

		if (type == SettingsController.class) {
			return new SettingsController(emailSettings);
		}

		throw new IllegalArgumentException("Unknown controller: " + type.getName());
	}
}
