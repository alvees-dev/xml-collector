package com.alveesdev.xmlcollector.infra.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.alveesdev.xmlcollector.application.port.ArchivedDocuments;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public class XmlArchivedDocuments implements ArchivedDocuments {

	private static final Set<DocumentModel> ALL_MODELS = EnumSet.allOf(DocumentModel.class);

	private final DocumentReader reader;

	public XmlArchivedDocuments(DocumentReader reader) {
		this.reader = reader;
	}

	@Override
	public Set<String> cnpjsIn(Path directory) throws IOException {

		Set<String> cnpjs = new TreeSet<>();

		for (Path file : xmlFilesIn(directory)) {
			// The key of a purchase has the CNPJ of the supplier, not of the company these documents belong to.
			if (isPurchase(directory, file)) {
				continue;
			}

			Optional<AccessKey> key = AccessKey.findIn(file.getFileName().toString());

			if (key.isEmpty()) {
				key = keyInContentOf(file);
			}

			key.map(AccessKey::cnpj).ifPresent(cnpjs::add);
		}

		return cnpjs;
	}

	private static boolean isPurchase(Path directory, Path file) {
		for (Path folder : directory.relativize(file)) {
			if (folder.toString().equalsIgnoreCase(DocumentCategory.NFE_PURCHASES.folderName())) {
				return true;
			}
		}

		return false;
	}

	private static List<Path> xmlFilesIn(Path directory) throws IOException {
		try (Stream<Path> files = Files.walk(directory)) {
			return files
					.filter(Files::isRegularFile)
					.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
					.toList();
		}
	}

	private Optional<AccessKey> keyInContentOf(Path file) throws IOException {

		XmlDocument document = reader.read(Files.readAllBytes(file), ALL_MODELS);

		if (document instanceof XmlDocument.Invoice invoice) {
			return Optional.of(invoice.key());
		}

		if (document instanceof XmlDocument.CancelEvent event) {
			return Optional.of(event.cancelledKey());
		}

		return Optional.empty();
	}
}
