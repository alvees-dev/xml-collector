package com.alveesdev.xmlcollector.infra.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.DefaultEmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.PdvType;

class PropertiesSettingsRepositoryTest {

	private static final String PASSWORD = "senha-de-teste";
	private static final EmailAccount DEFAULT_ACCOUNT = new EmailAccount(
			"smtp.gmail.com", 587, EmailSecurity.STARTTLS, "envio@exemplo.com", "senha-padrao");

	@TempDir
	Path directory;

	private Path file;
	private Path legacyFile;
	private PropertiesSettingsRepository repository;

	@BeforeEach
	void setUp() {
		file = directory.resolve("settings.properties");
		legacyFile = directory.resolve("email.properties");
		repository = new PropertiesSettingsRepository(file, legacyFile, DefaultEmailAccount.get());
	}

	@Test
	void usesTheDefaultAccountWhenThereIsNoFile() throws IOException {
		assertEquals(DEFAULT_ACCOUNT, repositoryWithDefaultAccount().load().emailAccount());
	}

	@Test
	void usesTheDefaultAccountWhenTheSavedOneIsIncomplete() throws IOException {
		Files.writeString(file, "smtp.username=\nsmtp.password=\n", StandardCharsets.UTF_8);

		assertEquals(DEFAULT_ACCOUNT, repositoryWithDefaultAccount().load().emailAccount());
	}

	@Test
	void keepsAnotherAccountSavedByTheUser() throws IOException {
		PropertiesSettingsRepository repository = repositoryWithDefaultAccount();

		repository.save(sampleSettings());

		assertEquals(sampleSettings().emailAccount(), repository.load().emailAccount());
	}

	@Test
	void doesNotCopyTheDefaultAccountToTheFile() throws IOException {
		PropertiesSettingsRepository repository = repositoryWithDefaultAccount();

		repository.save(sampleSettings().withEmailAccount(DEFAULT_ACCOUNT));

		String content = Files.readString(file);

		assertFalse(content.contains(DEFAULT_ACCOUNT.username()));
		assertFalse(content.contains("smtp.password"));
		assertEquals(DEFAULT_ACCOUNT, repository.load().emailAccount());
	}

	@Test
	void aNewDefaultPasswordReachesInstallationsThatAlreadySaved() throws IOException {
		repositoryWithDefaultAccount().save(sampleSettings().withEmailAccount(DEFAULT_ACCOUNT));

		EmailAccount rotated = new EmailAccount("smtp.gmail.com", 587, EmailSecurity.STARTTLS, DEFAULT_ACCOUNT.username(), "senha-nova");

		assertEquals(rotated, new PropertiesSettingsRepository(file, legacyFile, rotated).load().emailAccount());
	}

	private PropertiesSettingsRepository repositoryWithDefaultAccount() {
		return new PropertiesSettingsRepository(file, legacyFile, DEFAULT_ACCOUNT);
	}

	@Test
	void usesDefaultsWhenThereIsNoFile() throws IOException {
		assertEquals(AppSettings.defaults(), repository.load());
	}

	@Test
	void restoresEverythingThatWasSaved() throws IOException {
		AppSettings settings = sampleSettings();

		repository.save(settings);

		assertEquals(settings, new PropertiesSettingsRepository(file, legacyFile, DefaultEmailAccount.get()).load());
	}

	@Test
	void doesNotKeepThePasswordInPlainText() throws IOException {
		repository.save(sampleSettings());

		assertFalse(Files.readString(file).contains(PASSWORD));
	}

	@Test
	void readsTheLegacyEmailFileAndRemovesItAfterSaving() throws IOException {
		Files.writeString(legacyFile, "smtp.username=antigo@exemplo.com\nmessage.subject=Antigo\n", StandardCharsets.UTF_8);

		AppSettings loaded = repository.load();

		assertEquals("antigo@exemplo.com", loaded.emailAccount().username());
		assertEquals("Antigo", loaded.emailDraft().subject());
		assertEquals(AppSettings.defaults().pdvDirectories(), loaded.pdvDirectories());
		assertTrue(Files.exists(legacyFile));

		repository.save(loaded);

		assertFalse(Files.exists(legacyFile));
		assertEquals("antigo@exemplo.com", repository.load().emailAccount().username());
	}

	@Test
	void prefersTheCurrentFileOverTheLegacyOne() throws IOException {
		repository.save(sampleSettings());
		Files.writeString(legacyFile, "smtp.username=antigo@exemplo.com\n", StandardCharsets.UTF_8);

		assertEquals("usuario@exemplo.com", repository.load().emailAccount().username());
	}

	@Test
	void fallsBackOnInvalidValues() throws IOException {
		Files.writeString(file, "archive.organizeMode=XYZ\nsmtp.port=abc\nsmtp.security=XYZ\n", StandardCharsets.UTF_8);

		AppSettings loaded = repository.load();
		AppSettings defaults = AppSettings.defaults();

		assertEquals(defaults.organizeMode(), loaded.organizeMode());
		assertEquals(defaults.emailAccount().port(), loaded.emailAccount().port());
		assertEquals(defaults.emailAccount().security(), loaded.emailAccount().security());
	}

	@Test
	void readsTheOldSalesDirectoriesAsNfeDirectories() throws IOException {
		Files.writeString(file, "directories.nfe.sales.0=E\\:\\\\vendas\n", StandardCharsets.UTF_8);

		assertEquals(List.of("E:\\vendas"), repository.load().nfeDirectories());
	}

	@Test
	void reportsAMalformedFile() throws IOException {
		Files.writeString(file, "ruim=\\uZZZZ\n", StandardCharsets.UTF_8);

		assertThrows(IOException.class, repository::load);
	}

	private static AppSettings sampleSettings() {
		return new AppSettings(
				List.of("\\\\PDV01\\c$\\XML Notas", "D:\\Pasta com acentuação\\ç", ""),
				List.of("E:\\retaguarda", "F:\\retaguarda2"),
				PdvType.FOOD,
				"modelo-pdv",
				"modelo-nfe",
				OrganizeMode.BY_DAY,
				new EmailAccount("smtp.gmail.com", 587, EmailSecurity.STARTTLS, "usuario@exemplo.com", PASSWORD),
				new EmailDraft("dest@exemplo.com", "Assunto = ç", "linha 1\nlinha 2"));
	}
}
