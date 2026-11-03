package com.alveesdev.xmlcollector.infra.sefaz;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Clock;

import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;

public class SefazVoidingGatewayFactory implements VoidingGatewayFactory {

	private final Clock clock;

	public SefazVoidingGatewayFactory(Clock clock) {
		this.clock = clock;
	}

	@Override
	public Connection open(CertificateSettings settings) throws VoidingException {

		if (!settings.isComplete()) {
			throw new VoidingException("Informe o certificado e a senha em Configurações > Certificado.");
		}

		A1Certificate certificate = load(settings);

		if (certificate.isExpired(clock.instant())) {
			throw new VoidingException("O certificado digital está vencido.");
		}

		String cnpj = certificate.cnpj()
				.orElseThrow(() -> new VoidingException("Não foi possível identificar o CNPJ do certificado. Use um e-CNPJ A1."));

		try {
			return new Connection(
					new SefazVoidingGateway(certificate, settings.environment()),
					new CertificateInfo(cnpj, certificate.validUntil()));
		} catch (GeneralSecurityException | IOException e) {
			throw new VoidingException("Não foi possível preparar a conexão com a Sefaz: " + e.getMessage(), e);
		}
	}

	private static A1Certificate load(CertificateSettings settings) throws VoidingException {
		try {
			return A1Certificate.load(Path.of(settings.path()), settings.password().toCharArray());
		} catch (NoSuchFileException | InvalidPathException e) {
			throw new VoidingException("Arquivo do certificado não encontrado: " + settings.path(), e);
		} catch (IOException | GeneralSecurityException e) {
			throw new VoidingException("Não foi possível abrir o certificado. Confira o arquivo e a senha.", e);
		}
	}
}
