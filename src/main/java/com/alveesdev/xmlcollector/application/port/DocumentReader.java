package com.alveesdev.xmlcollector.application.port;

import java.util.Set;

import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public interface DocumentReader {

	XmlDocument read(byte[] content, Set<DocumentModel> acceptedModels);
}
