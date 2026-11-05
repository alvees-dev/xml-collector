package com.alveesdev.xmlcollector.ui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.settings.EmailAccount;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;

// A password only ever appears here as the fact that it changed.
public final class SettingsChanges {

	private SettingsChanges() {
	}

	public static List<String> between(AppSettings before, AppSettings after, Function<String, String> pdvModelNames, Function<String, String> nfeModelNames) {

		List<String> changes = new ArrayList<>();

		replaced(changes, "Tipo de PDV", before.pdvType().label(), after.pdvType().label());
		replaced(changes, "Modelo de busca PDV", pdvModelNames.apply(before.pdvModelId()), pdvModelNames.apply(after.pdvModelId()));
		replaced(changes, "Modelo de busca NFe", nfeModelNames.apply(before.nfeModelId()), nfeModelNames.apply(after.nfeModelId()));
		directories(changes, "PDV", before.pdvDirectories(), after.pdvDirectories());
		directories(changes, "NFe", before.nfeDirectories(), after.nfeDirectories());
		replaced(changes, "Organização dos arquivos", organizeLabel(before.organizeMode()), organizeLabel(after.organizeMode()));
		replaced(changes, "Separação dos arquivos", before.separationMode().label(), after.separationMode().label());
		email(changes, before.emailAccount(), after.emailAccount());
		certificate(changes, before.certificate(), after.certificate());

		return changes;
	}

	private static void replaced(List<String> changes, String name, String before, String after) {
		if (!Objects.equals(before, after)) {
			changes.add(name + " alterado: " + shown(before) + " -> " + shown(after));
		}
	}

	private static void directories(List<String> changes, String name, List<String> before, List<String> after) {
		if (!before.equals(after)) {
			changes.add("Diretórios " + name + " salvos: " + String.join(", ", after));
		}
	}

	private static void email(List<String> changes, EmailAccount before, EmailAccount after) {
		replaced(changes, "Servidor de e-mail", serverOf(before), serverOf(after));
		replaced(changes, "Usuário de e-mail", before.username(), after.username());

		if (!Objects.equals(before.password(), after.password())) {
			changes.add("Senha de e-mail alterada");
		}
	}

	private static String serverOf(EmailAccount account) {
		return account.host() + ":" + account.port() + " (" + account.security() + ")";
	}

	private static void certificate(List<String> changes, CertificateSettings before, CertificateSettings after) {
		if (!before.path().equals(after.path())) {
			changes.add("Certificado atualizado: " + shown(after.path()));
		}

		if (!before.password().equals(after.password())) {
			changes.add("Senha do certificado alterada");
		}

		replaced(changes, "Ambiente da Sefaz", VoidingText.environment(before.environment()), VoidingText.environment(after.environment()));
	}

	private static String organizeLabel(OrganizeMode mode) {
		return mode == OrganizeMode.BY_DAY ? "Separado por dia" : "Diretório único";
	}

	private static String shown(String value) {
		return value == null || value.isBlank() ? "(vazio)" : value;
	}
}
