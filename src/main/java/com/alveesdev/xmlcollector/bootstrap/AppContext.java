package com.alveesdev.xmlcollector.bootstrap;

import java.time.Clock;

import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.dispatch.DispatchService;
import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.IssuedNumberRepository;
import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.application.settings.SearchModelCatalog;
import com.alveesdev.xmlcollector.application.settings.service.SettingsService;
import com.alveesdev.xmlcollector.application.voiding.VoidingBatch;
import com.alveesdev.xmlcollector.infra.archive.MonthZipArchiver;
import com.alveesdev.xmlcollector.infra.archive.XmlArchive;
import com.alveesdev.xmlcollector.infra.archive.XmlArchivedDocuments;
import com.alveesdev.xmlcollector.infra.drive.GoogleDriveStorage;
import com.alveesdev.xmlcollector.infra.email.SmtpEmailSender;
import com.alveesdev.xmlcollector.infra.persistence.SqliteIssuedNumberRepository;
import com.alveesdev.xmlcollector.infra.search.SearchModelLoader;
import com.alveesdev.xmlcollector.infra.sefaz.SefazVoidingGatewayFactory;
import com.alveesdev.xmlcollector.infra.settings.BundledEmailAccount;
import com.alveesdev.xmlcollector.infra.settings.PropertiesSettingsRepository;
import com.alveesdev.xmlcollector.infra.xml.FiscalXmlReader;
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner;
import com.alveesdev.xmlcollector.ui.controller.CollectionTabController;
import com.alveesdev.xmlcollector.ui.controller.DispatchTabController;
import com.alveesdev.xmlcollector.ui.controller.MainScreenController;
import com.alveesdev.xmlcollector.ui.controller.MissingTabController;
import com.alveesdev.xmlcollector.ui.controller.SettingsController;
import com.alveesdev.xmlcollector.ui.view.SettingsWindow;

public final class AppContext {

	private final AppPaths paths;
	private final IssuedNumberRepository issuedNumbers;
	private final CollectionService collectionService;
	private final VoidingBatch voidingBatch;
	private final DispatchService dispatchService;
	private final SettingsService settingsService;
	private final SearchModelCatalog searchModels;
	private final SettingsWindow settingsWindow;

	public AppContext(AppPaths paths) {
		this.paths = paths;

		DocumentReader reader = new FiscalXmlReader();

		this.issuedNumbers = new SqliteIssuedNumberRepository(paths.databaseFile());
		DocumentArchive archive = new XmlArchive(reader);
		Clock clock = Clock.systemDefaultZone();

		this.collectionService = new CollectionService(new XmlFileScanner(), reader, archive, issuedNumbers);
		this.voidingBatch = new VoidingBatch(new SefazVoidingGatewayFactory(clock), archive, clock);
		this.dispatchService = new DispatchService(
				new XmlArchivedDocuments(reader),
				new MonthZipArchiver(),
				new SmtpEmailSender(),
				new GoogleDriveStorage(paths.googleCredentialsFile(), paths.googleTokensDir()));
		this.settingsService = new SettingsService(
				new PropertiesSettingsRepository(paths.settingsFile(), paths.legacyEmailSettingsFile(), BundledEmailAccount.load()));
		this.searchModels = new SearchModelCatalog(SearchModelLoader.load());
		this.settingsWindow = new SettingsWindow(this::createController);
	}

	public AppPaths paths() {
		return paths;
	}

	public SettingsService settings() {
		return settingsService;
	}

	public IssuedNumberRepository issuedNumbers() {
		return issuedNumbers;
	}

	public Object createController(Class<?> type) {
		if (type == MainScreenController.class) {
			return new MainScreenController(settingsWindow);
		}

		if (type == CollectionTabController.class) {
			return new CollectionTabController(collectionService, settingsService, searchModels, paths);
		}

		if (type == MissingTabController.class) {
			return new MissingTabController(collectionService, settingsService, searchModels, paths);
		}

		if (type == DispatchTabController.class) {
			return new DispatchTabController(dispatchService, settingsService, paths);
		}

		if (type == SettingsController.class) {
			return new SettingsController(settingsService, searchModels, voidingBatch);
		}

		throw new IllegalArgumentException("Unknown controller: " + type.getName());
	}
}
