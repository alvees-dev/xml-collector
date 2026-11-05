package com.alveesdev.xmlcollector.infra.xml;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.application.port.Failures;
import com.alveesdev.xmlcollector.domain.model.AccessKey;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

public class FiscalXmlReader implements DocumentReader {

	private static final String CANCEL_EVENT_TYPE = "110111";
	private static final String NFE_ID_PREFIX = "NFe";
	private static final String EVENT_ID_PREFIX = "ID";
	private static final String EVENT_SEQUENCE_PATTERN = "[0-9]{2}";

	private final XmlDocumentLoader loader = new XmlDocumentLoader();

	@Override
	public XmlDocument read(byte[] content, Set<DocumentModel> acceptedModels) {

		Document document;

		try {
			document = loader.load(content);
		} catch (IOException | SAXException e) {
			return new XmlDocument.Unreadable(Failures.describe(e));
		}

		try {
			Element invoice = firstElement(document, "infNFe");

			if (invoice != null) {
				return readInvoice(invoice, acceptedModels);
			}

			Element event = firstElement(document, "evento");

			if (event != null) {
				return readEvent(event, acceptedModels);
			}

			Element voiding = firstElement(document, "retInutNFe");

			if (voiding != null) {
				return readVoiding(voiding, acceptedModels);
			}

			return new XmlDocument.Ignored("not an invoice, a cancellation event or a voiding");

		} catch (XmlFormatException e) {
			return new XmlDocument.Unreadable(e.getMessage());
		}
	}

	private static XmlDocument readInvoice(Element invoice, Set<DocumentModel> acceptedModels) throws XmlFormatException {

		String id = invoice.getAttribute("Id").trim();

		if (!id.startsWith(NFE_ID_PREFIX)) {
			throw new XmlFormatException("infNFe Id attribute is missing or malformed");
		}

		AccessKey key = toKey(id.substring(NFE_ID_PREFIX.length()));

		if (!isAccepted(key, acceptedModels)) {
			return new XmlDocument.Ignored("document model " + key.modelCode() + " is not accepted");
		}

		Element identification = requiredChild(invoice, "ide");
		int series = requiredInt(identification, "serie");
		int number = requiredInt(identification, "nNF");
		OffsetDateTime issuedAt = requiredDateTime(identification, "dhEmi");
		Element totals = requiredChild(requiredChild(invoice, "total"), "ICMSTot");
		BigDecimal totalValue = requiredDecimal(totals, "vNF");

		return new XmlDocument.Invoice(key, series, number, issuedAt, totalValue);
	}

	private static XmlDocument readEvent(Element event, Set<DocumentModel> acceptedModels) throws XmlFormatException {

		Element info = requiredChild(event, "infEvento");
		String type = requiredText(info, "tpEvento");

		if (!CANCEL_EVENT_TYPE.equals(type)) {
			return new XmlDocument.Ignored("event type " + type + " is not a cancellation");
		}

		AccessKey cancelledKey = toKey(requiredText(info, "chNFe"));

		if (!isAccepted(cancelledKey, acceptedModels)) {
			return new XmlDocument.Ignored("document model " + cancelledKey.modelCode() + " is not accepted");
		}

		String eventId = toEventId(info.getAttribute("Id").trim(), cancelledKey);
		OffsetDateTime eventAt = requiredDateTime(info, "dhEvento");

		return new XmlDocument.CancelEvent(eventId, cancelledKey, eventAt);
	}

	private static XmlDocument readVoiding(Element voiding, Set<DocumentModel> acceptedModels) throws XmlFormatException {

		Element info = requiredChild(voiding, "infInut");
		String status = requiredText(info, "cStat");
		Element environment = child(info, "tpAmb");

		if (environment != null && FiscalEnvironment.HOMOLOGATION.code().equals(environment.getTextContent().trim())) {
			return new XmlDocument.Ignored("voiding of the homologation environment has no fiscal effect");
		}

		if (!VoidingReceipt.APPROVED_STATUS.equals(status)) {
			return new XmlDocument.Ignored("voiding was not approved, status " + status);
		}

		String modelCode = requiredText(info, "mod");

		if (DocumentModel.fromCode(modelCode).filter(acceptedModels::contains).isEmpty()) {
			return new XmlDocument.Ignored("document model " + modelCode + " is not accepted");
		}

		int series = requiredInt(info, "serie");
		int start = requiredInt(info, "nNFIni");
		int end = requiredInt(info, "nNFFin");

		if (start > end) {
			throw new XmlFormatException("voiding range starts after it ends");
		}

		NumberRange range = new NumberRange(start, end);
		String cnpj = requiredText(info, "CNPJ");
		String id = VoidingRequest.idOf(requiredText(info, "cUF"), requiredText(info, "ano"), cnpj, modelCode, series, range);

		return new XmlDocument.Voiding(id, cnpj, modelCode, series, range, requiredDateTime(info, "dhRecbto"));
	}

	private static boolean isAccepted(AccessKey key, Set<DocumentModel> acceptedModels) {
		return key.model().filter(acceptedModels::contains).isPresent();
	}

	private static String toEventId(String id, AccessKey cancelledKey) throws XmlFormatException {

		if (!id.startsWith(EVENT_ID_PREFIX)) {
			throw new XmlFormatException("infEvento Id attribute is missing or malformed");
		}

		String eventId = id.substring(EVENT_ID_PREFIX.length());
		String start = CANCEL_EVENT_TYPE + cancelledKey.value();

		if (!eventId.startsWith(start) || !eventId.substring(start.length()).matches(EVENT_SEQUENCE_PATTERN)) {
			throw new XmlFormatException("infEvento Id does not match the cancelled access key");
		}

		return eventId;
	}

	private static AccessKey toKey(String text) throws XmlFormatException {
		try {
			return new AccessKey(text.trim());
		} catch (IllegalArgumentException e) {
			throw new XmlFormatException(e.getMessage());
		}
	}

	private static Element firstElement(Document document, String tag) {
		NodeList nodes = document.getElementsByTagName(tag);
		return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
	}

	private static Element child(Element parent, String tag) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && tag.equals(element.getTagName())) {
				return element;
			}
		}
		return null;
	}

	private static Element requiredChild(Element parent, String tag) throws XmlFormatException {
		Element element = child(parent, tag);

		if (element == null) {
			throw new XmlFormatException("missing element <" + tag + ">");
		}

		return element;
	}

	private static String requiredText(Element parent, String tag) throws XmlFormatException {
		String text = requiredChild(parent, tag).getTextContent().trim();

		if (text.isEmpty()) {
			throw new XmlFormatException("empty element <" + tag + ">");
		}

		return text;
	}

	private static int requiredInt(Element parent, String tag) throws XmlFormatException {
		String text = requiredText(parent, tag);

		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			throw new XmlFormatException("invalid number in <" + tag + ">: " + text);
		}
	}

	private static BigDecimal requiredDecimal(Element parent, String tag) throws XmlFormatException {
		String text = requiredText(parent, tag);

		try {
			return new BigDecimal(text);
		} catch (NumberFormatException e) {
			throw new XmlFormatException("invalid value in <" + tag + ">: " + text);
		}
	}

	private static OffsetDateTime requiredDateTime(Element parent, String tag) throws XmlFormatException {
		String text = requiredText(parent, tag);

		try {
			return OffsetDateTime.parse(text);
		} catch (DateTimeParseException e) {
			throw new XmlFormatException("invalid date in <" + tag + ">: " + text);
		}
	}
}