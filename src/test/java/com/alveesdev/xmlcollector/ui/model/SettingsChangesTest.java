package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.application.settings.EmailDraft;
import com.alveesdev.xmlcollector.application.settings.EmailSecurity;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.PdvType;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;

class SettingsChangesTest {

	private static final String EMAIL_PASSWORD = "segredo-do-email";
	private static final String CERTIFICATE_PASSWORD = "segredo-do-certificado";
	private static final UnaryOperator<String> MODEL_NAMES = id -> id.isEmpty() ? "Nenhum" : "Modelo " + id;

	private static final AppSettings BEFORE = new AppSettings(
			List.of("\\\\pdv1"),
			List.of("\\\\nfe1"),
			"",
			PdvType.PDV,
			"a",
			"",
			OrganizeMode.SINGLE_FOLDER,
			SeparationMode.ALL_TOGETHER,
			new EmailAccount("smtp.exemplo.com", 587, EmailSecurity.values()[0], "envio@exemplo.com", EMAIL_PASSWORD),
			EmailDraft.empty(),
			new CertificateSettings("C:\\antigo.pfx", CERTIFICATE_PASSWORD, FiscalEnvironment.HOMOLOGATION));

	@Test
	void nothingChangedSaysNothing() {
		assertEquals(List.of(), changesTo(BEFORE));
	}

	@Test
	void namesTheModelsInsteadOfTheirIds() {
		AppSettings after = new AppSettings(BEFORE.pdvDirectories(), BEFORE.nfeDirectories(), BEFORE.serverDirectory(), BEFORE.pdvType(), "b", "c",
				BEFORE.organizeMode(), BEFORE.separationMode(), BEFORE.emailAccount(), BEFORE.emailDraft(), BEFORE.certificate());

		assertEquals(List.of(
				"Modelo de busca PDV alterado: Modelo a -> Modelo b",
				"Modelo de busca NFe alterado: Nenhum -> Modelo c"), changesTo(after));
	}

	@Test
	void listsTheDirectoriesThatWereSaved() {
		AppSettings after = new AppSettings(List.of("\\\\pdv1", "\\\\pdv2"), BEFORE.nfeDirectories(), BEFORE.serverDirectory(), BEFORE.pdvType(), BEFORE.pdvModelId(), BEFORE.nfeModelId(),
				BEFORE.organizeMode(), BEFORE.separationMode(), BEFORE.emailAccount(), BEFORE.emailDraft(), BEFORE.certificate());

		assertEquals(List.of("Diretórios PDV salvos: \\\\pdv1, \\\\pdv2"), changesTo(after));
	}

	@Test
	void recordsTheServerDirectory() {
		AppSettings after = new AppSettings(BEFORE.pdvDirectories(), BEFORE.nfeDirectories(), "\\\\servidor\\xml", BEFORE.pdvType(), BEFORE.pdvModelId(),
				BEFORE.nfeModelId(), BEFORE.organizeMode(), BEFORE.separationMode(), BEFORE.emailAccount(), BEFORE.emailDraft(), BEFORE.certificate());

		assertEquals(List.of("Diretório Servidor salvo: \\\\servidor\\xml"), changesTo(after));
	}

	@Test
	void describesTheOptionsWithTheirLabels() {
		AppSettings after = new AppSettings(BEFORE.pdvDirectories(), BEFORE.nfeDirectories(), BEFORE.serverDirectory(), BEFORE.pdvType(), BEFORE.pdvModelId(), BEFORE.nfeModelId(),
				OrganizeMode.BY_DAY, SeparationMode.BY_PDV, BEFORE.emailAccount(), BEFORE.emailDraft(), BEFORE.certificate());

		assertEquals(List.of(
				"Organização dos arquivos alterado: Diretório único -> Separado por dia",
				"Separação dos arquivos alterado: Tudo junto -> Por PDV"), changesTo(after));
	}

	@Test
	void recordsTheCertificateAndItsEnvironment() {
		AppSettings after = BEFORE.withEmailAccount(BEFORE.emailAccount());
		after = new AppSettings(after.pdvDirectories(), after.nfeDirectories(), after.serverDirectory(), after.pdvType(), after.pdvModelId(), after.nfeModelId(),
				after.organizeMode(), after.separationMode(), after.emailAccount(), after.emailDraft(),
				new CertificateSettings("C:\\novo.pfx", CERTIFICATE_PASSWORD, FiscalEnvironment.PRODUCTION));

		assertEquals(List.of(
				"Certificado atualizado: C:\\novo.pfx",
				"Ambiente da Sefaz alterado: Homologação -> Produção"), changesTo(after));
	}

	@Test
	void aChangedPasswordIsRecordedWithoutItsValue() {
		String newEmailPassword = "novo-segredo-do-email";
		String newCertificatePassword = "novo-segredo-do-certificado";

		AppSettings after = new AppSettings(BEFORE.pdvDirectories(), BEFORE.nfeDirectories(), BEFORE.serverDirectory(), BEFORE.pdvType(), BEFORE.pdvModelId(), BEFORE.nfeModelId(),
				BEFORE.organizeMode(), BEFORE.separationMode(),
				new EmailAccount("smtp.exemplo.com", 587, EmailSecurity.values()[0], "envio@exemplo.com", newEmailPassword),
				BEFORE.emailDraft(),
				new CertificateSettings("C:\\antigo.pfx", newCertificatePassword, FiscalEnvironment.HOMOLOGATION));

		List<String> changes = changesTo(after);

		assertEquals(List.of("Senha de e-mail alterada", "Senha do certificado alterada"), changes);

		for (String secret : List.of(EMAIL_PASSWORD, CERTIFICATE_PASSWORD, newEmailPassword, newCertificatePassword)) {
			assertFalse(String.join(" ", changes).contains(secret), secret);
		}
	}

	private static List<String> changesTo(AppSettings after) {
		return SettingsChanges.between(BEFORE, after, MODEL_NAMES, MODEL_NAMES);
	}
}
