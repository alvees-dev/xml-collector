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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.application.voiding.VoidingProgress.Stage;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;

public class VoidingBatch {

	private static final Logger LOG = Logger.getLogger(VoidingBatch.class.getName());
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

	// The answer of the Sefaz exists only in memory until it is saved, so this has to be known before anything goes out.
	public void checkArchiveFolder(Path archiveRoot, LocalDate archiveDate) throws VoidingException {
		Path folder = ArchiveLayout.voidingDirectoryFor(archiveRoot, archiveDate);

		try {
			archive.requireWritable(folder);
		} catch (IOException e) {
			throw new VoidingException("Não é possível gravar na pasta de arquivos: " + folder
					+ "\n\nNada foi enviado à Sefaz. Confira o acesso a essa pasta.", e);
		}
	}

	public List<VoidingOutcome> run(List<VoidingRequest> requests, CertificateSettings settings,
			Path archiveRoot, LocalDate archiveDate, Map<Integer, List<Path>> pdvFolders, Consumer<VoidingProgress> listener,
			BooleanSupplier cancelled) throws VoidingException {

		VoidingGatewayFactory.Connection connection = gateways.open(settings);
		requireSameCompany(connection.certificate(), requests);
		checkPdvFolders(pdvFolders);
		checkArchiveFolder(archiveRoot, archiveDate);

		VoidingService service = new VoidingService(connection.gateway(), archive, clock, settings.environment());
		List<VoidingOutcome> outcomes = new ArrayList<>();
		boolean stopped = false;

		for (int index = 0; index < requests.size(); index++) {
			VoidingRequest request = requests.get(index);

			if (stopped || cancelled.getAsBoolean()) {
				record(outcomes, new VoidingOutcome(request, Stage.NOT_SENT, null), settings);
				listener.accept(new VoidingProgress(index, Stage.NOT_SENT));
				continue;
			}

			listener.accept(new VoidingProgress(index, Stage.SENDING));

			try {
				VoidingResult result = service.voidRange(request, archiveRoot, archiveDate, pdvFolders.getOrDefault(request.series(), List.of()));
				Stage stage = result.receipt().approved() ? Stage.APPROVED : Stage.REJECTED;

				record(outcomes, new VoidingOutcome(request, stage, result.receipt().status() + " - " + result.receipt().reason(),
						result.archiveFailure(), result.pdvFailures(), result.unsavedXml()), settings);
				listener.accept(new VoidingProgress(index, stage));

				// The file of this range is not safe yet, so no other one goes out until that is solved.
				stopped = !result.pdvFailures().isEmpty();

			} catch (VoidingException e) {
				// The answer is unknown, so nothing else goes out until someone checks the Sefaz.
				stopped = true;
				LOG.log(Level.WARNING, "No known answer for the voiding " + request.id(), e);
				record(outcomes, new VoidingOutcome(request, Stage.FAILED, e.getMessage()), settings);
				listener.accept(new VoidingProgress(index, Stage.FAILED));
			}
		}

		return outcomes;
	}

	// A voiding cannot be undone and the summary on the screen is gone once closed, so what happened to each range stays here.
	private static void record(List<VoidingOutcome> outcomes, VoidingOutcome outcome, CertificateSettings settings) {
		outcomes.add(outcome);

		boolean clean = outcome.stage() == Stage.APPROVED && outcome.archiveFailure() == null && outcome.pdvFailures().isEmpty();

		LOG.log(clean ? Level.INFO : Level.WARNING, "Voiding " + outcome.request().id() + " in " + settings.environment() + ": " + outcome.stage()
				+ ", detail: " + outcome.detail() + ", archive failure: " + outcome.archiveFailure() + ", PDV failures: " + outcome.pdvFailures());

		if (outcome.unsavedXml() != null) {
			LOG.severe("Approved voiding " + outcome.request().id() + " was not saved anywhere, its XML follows: " + outcome.unsavedXml());
		}
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
