package com.alveesdev.xmlcollector.application.voiding;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

public class VoidingBatch {

	private static final int CNPJ_ROOT_LENGTH = 8;

	private final VoidingGatewayFactory gateways;
	private final DocumentArchive archive;
	private final Clock clock;

	public VoidingBatch(VoidingGatewayFactory gateways, DocumentArchive archive, Clock clock) {
		this.gateways = gateways;
		this.archive = archive;
		this.clock = clock;
	}

	public VoidingGatewayFactory.CertificateInfo checkCertificate(CertificateSettings settings, List<VoidingRequest> requests)
			throws VoidingException {

		VoidingGatewayFactory.CertificateInfo certificate = gateways.open(settings).certificate();
		requireSameCompany(certificate, requests);

		return certificate;
	}

	public VoidingGatewayFactory.CertificateInfo checkCertificate(CertificateSettings settings) throws VoidingException {
		return gateways.open(settings).certificate();
	}

	public void checkPdvFolders(Map<Integer, List<Path>> pdvFolders) throws VoidingException {
		for (List<Path> folders : pdvFolders.values()) {
			for (Path folder : folders) {
				try {
					archive.requireWritable(folder);
				} catch (IOException e) {
					throw new VoidingException("Não é possível gravar na pasta de inutilização do PDV: " + folder
							+ "\n\nNada foi enviado à Sefaz. Confira o acesso a essa pasta.", e);
				}
			}
		}
	}

	public List<VoidingOutcome> run(List<VoidingRequest> requests, CertificateSettings settings,
			Path archiveRoot, LocalDate archiveDate, Map<Integer, List<Path>> pdvFolders, Consumer<VoidingProgress> listener,
			BooleanSupplier cancelled) throws VoidingException {

		VoidingGatewayFactory.Connection connection = gateways.open(settings);
		requireSameCompany(connection.certificate(), requests);
		checkPdvFolders(pdvFolders);

		VoidingService service = new VoidingService(connection.gateway(), archive, clock, settings.environment());
		List<VoidingOutcome> outcomes = new ArrayList<>();
		boolean stopped = false;

		for (int index = 0; index < requests.size(); index++) {
			VoidingRequest request = requests.get(index);

			if (stopped || cancelled.getAsBoolean()) {
				outcomes.add(new VoidingOutcome(request, Stage.NOT_SENT, null));
				listener.accept(new VoidingProgress(index, Stage.NOT_SENT));
				continue;
			}

			listener.accept(new VoidingProgress(index, Stage.SENDING));

			try {
				VoidingResult result = service.voidRange(request, archiveRoot, archiveDate, pdvFolders.getOrDefault(request.series(), List.of()));
				Stage stage = result.receipt().approved() ? Stage.APPROVED : Stage.REJECTED;

				outcomes.add(new VoidingOutcome(request, stage, result.receipt().status() + " - " + result.receipt().reason(),
						result.archiveFailure(), result.pdvFailures()));
				listener.accept(new VoidingProgress(index, stage));

				// The file of this range is not safe yet, so no other one goes out until that is solved.
				stopped = !result.pdvFailures().isEmpty();

			} catch (VoidingException e) {
				// The answer is unknown, so nothing else goes out until someone checks the Sefaz.
				stopped = true;
				outcomes.add(new VoidingOutcome(request, Stage.FAILED, e.getMessage()));
				listener.accept(new VoidingProgress(index, Stage.FAILED));
			}
		}

		return outcomes;
	}

	private static void requireSameCompany(VoidingGatewayFactory.CertificateInfo certificate, List<VoidingRequest> requests)
			throws VoidingException {

		for (VoidingRequest request : requests) {
			if (!request.cnpj().startsWith(certificate.cnpj().substring(0, CNPJ_ROOT_LENGTH))) {
				throw new VoidingException("O certificado é do CNPJ " + certificate.cnpj()
						+ ", mas as notas são do CNPJ " + request.cnpj() + ".");
			}
		}
	}
}
