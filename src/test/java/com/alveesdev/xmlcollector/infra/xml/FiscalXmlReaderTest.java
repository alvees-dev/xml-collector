package com.alveesdev.xmlcollector.infra.xml;

import static com.alveesdev.xmlcollector.domain.TestDocuments.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class FiscalXmlReaderTest {

	private static final String KEY = key(7).value();
	private static final String NFE_MODEL_KEY = KEY.substring(0, 20) + "55" + KEY.substring(22);

	private final FiscalXmlReader reader = new FiscalXmlReader();

	@Test
	void readsAnNfce() {
		XmlDocument.Invoice invoice = assertInstanceOf(XmlDocument.Invoice.class, read(invoiceXml(KEY)));

		assertEquals(KEY, invoice.key().value());
		assertEquals(2, invoice.series());
		assertEquals(7, invoice.number());
		assertEquals(LocalDate.of(2026, 1, 15), invoice.date());
		assertEquals(new BigDecimal("10.50"), invoice.totalValue());
	}

	@Test
	void ignoresAnInvoiceOfAnotherModel() {
		assertInstanceOf(XmlDocument.Ignored.class, read(invoiceXml(NFE_MODEL_KEY)));
	}

	@Test
	void readsACancellationEvent() {
		XmlDocument.CancelEvent event = assertInstanceOf(XmlDocument.CancelEvent.class,
				read(eventXml("110111", KEY, "ID110111" + KEY + "01")));

		assertEquals(KEY, event.cancelledKey().value());
		assertEquals("110111" + KEY + "01", event.eventId());
		assertEquals(LocalDate.of(2026, 1, 16), event.date());
	}

	@Test
	void readsAnApprovedVoiding() {
		XmlDocument.Voiding voiding = assertInstanceOf(XmlDocument.Voiding.class, read(voidingXml("102", "65")));

		assertEquals(1, voiding.series());
		assertEquals(new NumberRange(2, 3), voiding.range());
		assertEquals(LocalDate.of(2026, 2, 5), voiding.date());
	}

	@Test
	void ignoresARejectedVoiding() {
		assertInstanceOf(XmlDocument.Ignored.class, read(voidingXml("241", "65")));
	}

	@Test
	void ignoresAVoidingOfAnotherModel() {
		assertInstanceOf(XmlDocument.Ignored.class, read(voidingXml("102", "55")));
	}

	@Test
	void readsAVoidingWithTheRootAndNamespaceOfTheOfficialSchema() {
		String xml = "<ProcInutNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
				+ "<inutNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><infInut Id=\"ID1\"><serie>9</serie></infInut></inutNFe>"
				+ "<retInutNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infInut><tpAmb>1</tpAmb><verAplic>X</verAplic>"
				+ "<cStat>102</cStat><xMotivo>Inutilização de número homologado</xMotivo><cUF>35</cUF><ano>26</ano>"
				+ "<CNPJ>12345678000190</CNPJ><mod>65</mod><serie>1</serie><nNFIni>2</nNFIni><nNFFin>3</nNFFin>"
				+ "<dhRecbto>2026-02-05T10:00:00-03:00</dhRecbto><nProt>135</nProt></infInut></retInutNFe></ProcInutNFe>";

		XmlDocument.Voiding voiding = assertInstanceOf(XmlDocument.Voiding.class, read(xml));

		assertEquals(1, voiding.series());
		assertEquals("ID35261234567800019065001000000002000000003", voiding.id());
	}

	private static String voidingXml(String status, String model) {
		return "<procInutNFe><retInutNFe><infInut><cStat>" + status + "</cStat><cUF>35</cUF><ano>26</ano>"
				+ "<CNPJ>12345678000190</CNPJ><mod>" + model + "</mod><serie>1</serie><nNFIni>2</nNFIni><nNFFin>3</nNFFin>"
				+ "<dhRecbto>2026-02-05T10:00:00-03:00</dhRecbto></infInut></retInutNFe></procInutNFe>";
	}

	@Test
	void ignoresAnotherEventType() {
		assertInstanceOf(XmlDocument.Ignored.class, read(eventXml("110110", KEY, "ID110110" + KEY + "01")));
	}

	@Test
	void ignoresACancellationOfAnotherModel() {
		assertInstanceOf(XmlDocument.Ignored.class, read(eventXml("110111", NFE_MODEL_KEY, "ID110111" + NFE_MODEL_KEY + "01")));
	}

	@Test
	void rejectsAnEventIdThatDoesNotMatchTheCancelledKey() {
		String otherKey = key(8).value();

		assertInstanceOf(XmlDocument.Unreadable.class, read(eventXml("110111", KEY, "ID110111" + otherKey + "01")));
	}

	@Test
	void readsAnNfeWhenItsModelIsAccepted() {
		XmlDocument.Invoice invoice = assertInstanceOf(XmlDocument.Invoice.class,
				read(invoiceXml(NFE_MODEL_KEY), Set.of(DocumentModel.NFE)));

		assertEquals(NFE_MODEL_KEY, invoice.key().value());
	}

	@Test
	void readsACancellationOfAnNfeWhenItsModelIsAccepted() {
		assertInstanceOf(XmlDocument.CancelEvent.class,
				read(eventXml("110111", NFE_MODEL_KEY, "ID110111" + NFE_MODEL_KEY + "01"), Set.of(DocumentModel.NFE)));
	}

	@Test
	void ignoresAnNfceWhenOnlyNfeIsAccepted() {
		assertInstanceOf(XmlDocument.Ignored.class, read(invoiceXml(KEY), Set.of(DocumentModel.NFE)));
	}

	@Test
	void ignoresAnIncompleteInvoiceOfAModelThatIsNotAccepted() {
		String xml = invoiceXml(NFE_MODEL_KEY).replace("<nNF>7</nNF>", "");

		assertInstanceOf(XmlDocument.Ignored.class, read(xml));
	}

	@Test
	void ignoresAnUnknownModelEvenWhenAllModelsAreAccepted() {
		String otherModelKey = KEY.substring(0, 20) + "57" + KEY.substring(22);

		assertInstanceOf(XmlDocument.Ignored.class, read(invoiceXml(otherModelKey), EnumSet.allOf(DocumentModel.class)));
	}

	@Test
	void ignoresUnrelatedXml() {
		assertInstanceOf(XmlDocument.Ignored.class, read("<outro><coisa/></outro>"));
	}

	@Test
	void reportsMalformedXmlAsUnreadable() {
		assertInstanceOf(XmlDocument.Unreadable.class, read("<nao-fecha>"));
	}

	@Test
	void reportsAMalformedAccessKeyAsUnreadable() {
		assertInstanceOf(XmlDocument.Unreadable.class, read(invoiceXml("123")));
	}

	@Test
	void reportsAMissingRequiredFieldAsUnreadable() {
		String xml = invoiceXml(KEY).replace("<nNF>7</nNF>", "");

		assertInstanceOf(XmlDocument.Unreadable.class, read(xml));
	}

	@Test
	void rejectsADoctypeDeclaration() {
		String xml = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///c:/windows/win.ini\">]>" + invoiceXml(KEY);

		assertInstanceOf(XmlDocument.Unreadable.class, read(xml));
	}

	private XmlDocument read(String xml) {
		return read(xml, Set.of(DocumentModel.NFCE));
	}

	private XmlDocument read(String xml, Set<DocumentModel> acceptedModels) {
		return reader.read(xml.getBytes(StandardCharsets.UTF_8), acceptedModels);
	}

	private static String invoiceXml(String key) {
		return "<nfeProc><NFe><infNFe Id=\"NFe" + key + "\"><ide><serie>2</serie><nNF>7</nNF>"
				+ "<dhEmi>2026-01-15T10:00:00-03:00</dhEmi></ide>"
				+ "<total><ICMSTot><vNF>10.50</vNF></ICMSTot></total></infNFe></NFe></nfeProc>";
	}

	private static String eventXml(String type, String key, String id) {
		return "<procEventoNFe><evento><infEvento Id=\"" + id + "\"><chNFe>" + key + "</chNFe>"
				+ "<dhEvento>2026-01-16T10:00:00-03:00</dhEvento><tpEvento>" + type + "</tpEvento>"
				+ "</infEvento></evento></procEventoNFe>";
	}
}
