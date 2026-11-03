package com.alveesdev.xmlcollector.application.port;

import java.time.Instant;

import com.alveesdev.xmlcollector.application.settings.CertificateSettings;

public interface VoidingGatewayFactory {

	record CertificateInfo(String cnpj, Instant validUntil) {
	}

	record Connection(VoidingGateway gateway, CertificateInfo certificate) {
	}

	Connection open(CertificateSettings settings) throws VoidingException;
}
