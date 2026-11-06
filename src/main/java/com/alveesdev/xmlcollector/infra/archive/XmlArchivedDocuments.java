package com.alveesdev.xmlcollector.infra.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

	@Override
	public List<XmlDocument> documentsOfTheCompanyIn(Path directory) throws IOException {

		List<XmlDocument> documents = new ArrayList<>();

		for (Path file : xmlFilesIn(directory)) {
			if (!isPurchase(directory, file)) {
				documents.add(reader.read(Files.readAllBytes(file), DocumentModel.ALL));
			}
		}

		return documents;
	}

	@Override
	public List<XmlDocument> purchasesIn(Path directory) throws IOException {

		List<XmlDocument> documents = new ArrayList<>();

		for (Path file : xmlFilesIn(directory)) {
			if (isPurchase(directory, file)) {
				documents.add(reader.read(Files.readAllBytes(file), DocumentModel.ALL));
			}
		}

		return documents;
	}

	// The purchases sit in NFe\Compras, right inside the month, whatever day folders come below it.
	private static boolean isPurchase(Path directory, Path file) {
		return directory.relativize(file).startsWith(directory.getFileSystem().getPath(DocumentCategory.NFE_PURCHASES.folderName()));
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

		return switch (reader.read(Files.readAllBytes(file), DocumentModel.ALL)) {
			case XmlDocument.Invoice invoice -> Optional.of(invoice.key());
			case XmlDocument.CancelEvent event -> Optional.of(event.cancelledKey());
			case XmlDocument.Voiding _, XmlDocument.Ignored _, XmlDocument.Unreadable _ -> Optional.empty();
		};
	}
}
