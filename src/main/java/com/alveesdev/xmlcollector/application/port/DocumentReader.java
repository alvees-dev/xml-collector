package com.alveesdev.xmlcollector.application.port;

import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public interface DocumentReader {

	XmlDocument read(byte[] content);
}
