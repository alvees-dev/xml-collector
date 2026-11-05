package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public interface StubArchivedDocuments extends ArchivedDocuments {

	@Override
	default List<XmlDocument> documentsOfTheCompanyIn(Path directory) throws IOException {
		return List.of();
	}

	@Override
	default List<XmlDocument> purchasesIn(Path directory) throws IOException {
		return List.of();
	}
}
