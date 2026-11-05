package com.alveesdev.xmlcollector.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public interface ArchivedDocuments {

	Set<String> cnpjsIn(Path directory) throws IOException;

	default List<XmlDocument> documentsOfTheCompanyIn(Path directory) throws IOException {
		return List.of();
	}

	default List<XmlDocument> purchasesIn(Path directory) throws IOException {
		return List.of();
	}
}
