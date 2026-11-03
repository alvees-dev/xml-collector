package com.alveesdev.xmlcollector.application.voiding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.LocalDate;

import com.alveesdev.xmlcollector.application.port.DocumentArchive;
import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGateway;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.service.ArchiveLayout;

public class VoidingService {

	private final VoidingGateway gateway;
	private final DocumentArchive archive;
	private final Clock clock;

	public VoidingService(VoidingGateway gateway, DocumentArchive archive, Clock clock) {
		this.gateway = gateway;
		this.archive = archive;
		this.clock = clock;
	}

	public VoidingResult voidRange(VoidingRequest request, OrganizeMode organizeMode, Path archiveRoot) throws VoidingException {

		VoidingReceipt receipt = gateway.voidRange(request);

		if (!receipt.approved()) {
			return new VoidingResult(receipt, null);
		}

		return new VoidingResult(receipt, store(request, receipt, organizeMode, archiveRoot));
	}

	private String store(VoidingRequest request, VoidingReceipt receipt, OrganizeMode organizeMode, Path archiveRoot) {

		Path directory = ArchiveLayout.directoryFor(archiveRoot, LocalDate.now(clock), organizeMode, DocumentCategory.VOIDED);
		String name = ArchiveLayout.voidingFileName(request.id());

		try {
			archive.store(receipt.xml().getBytes(StandardCharsets.UTF_8), FileTime.from(clock.instant()), directory, name, name, request.id());
			return null;
		} catch (IOException e) {
			return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		}
	}
}
