package com.alveesdev.xmlcollector.application.voiding;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
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

	public List<VoidingOutcome> run(List<VoidingRequest> requests, CertificateSettings settings, OrganizeMode organizeMode,
			Path archiveRoot, Consumer<VoidingProgress> listener, BooleanSupplier cancelled) throws VoidingException {

		VoidingGatewayFactory.Connection connection = gateways.open(settings);
		requireSameCompany(connection.certificate(), requests);

		VoidingService service = new VoidingService(connection.gateway(), archive, clock, settings.environment());
		List<VoidingOutcome> outcomes = new ArrayList<>();
		boolean stopped = false;

		for (int index = 0; index < requests.size(); index++) {
			VoidingRequest request = requests.get(index);

			if (stopped || cancelled.getAsBoolean()) {
				outcomes.add(new VoidingOutcome(request, Stage.NOT_SENT, null, null));
				listener.accept(new VoidingProgress(index, Stage.NOT_SENT));
				continue;
			}

			listener.accept(new VoidingProgress(index, Stage.SENDING));

			try {
				VoidingResult result = service.voidRange(request, organizeMode, archiveRoot, List.of());
				Stage stage = result.receipt().approved() ? Stage.APPROVED : Stage.REJECTED;

				outcomes.add(new VoidingOutcome(request, stage, result.receipt().status() + " - " + result.receipt().reason(), result.archiveFailure()));
				listener.accept(new VoidingProgress(index, stage));

			} catch (VoidingException e) {
				// The answer is unknown, so nothing else goes out until someone checks the Sefaz.
				stopped = true;
				outcomes.add(new VoidingOutcome(request, Stage.FAILED, e.getMessage(), null));
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
