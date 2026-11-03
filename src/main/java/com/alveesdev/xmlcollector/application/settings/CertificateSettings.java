package com.alveesdev.xmlcollector.application.settings;

import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;

public record CertificateSettings(String path, String password, FiscalEnvironment environment) {

	public static CertificateSettings empty() {
		return new CertificateSettings("", "", FiscalEnvironment.HOMOLOGATION);
	}

	public boolean isComplete() {
		return !path.isBlank() && !password.isEmpty();
	}

	@Override
	public String toString() {
		return "CertificateSettings[path=" + path + ", environment=" + environment + "]";
	}
}
