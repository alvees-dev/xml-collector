package com.alveesdev.xmlcollector.infra.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class XmlDocumentLoader {

	private static final ThreadLocal<DocumentBuilder> BUILDER = ThreadLocal.withInitial(XmlDocumentLoader::createBuilder);

	private static final ErrorHandler STRICT_HANDLER = new ErrorHandler() {

		@Override
		public void warning(SAXParseException exception) {
		}

		@Override
		public void error(SAXParseException exception) throws SAXException {
			throw exception;
		}

		@Override
		public void fatalError(SAXParseException exception) throws SAXException {
			throw exception;
		}
	};

	public Document load(byte[] content) throws IOException, SAXException {
		DocumentBuilder builder = BUILDER.get();
		builder.reset();
		builder.setErrorHandler(STRICT_HANDLER);

		return builder.parse(new ByteArrayInputStream(content));
	}

	private static DocumentBuilder createBuilder() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			return factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("XML parser could not be configured", e);
		}
	}
}