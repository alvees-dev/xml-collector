package com.alveesdev.xmlcollector.infra.xml;

import static com.alveesdev.xmlcollector.domain.TestDocuments.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class NfceXmlReaderTest {

	private static final String KEY = key(7).value();
	private static final String NFE_MODEL_KEY = KEY.substring(0, 20) + "55" + KEY.substring(22);

	private final NfceXmlReader reader = new NfceXmlReader();

	@Test
	void readsAnNfce() {
		XmlDocument.Nfce nfce = assertInstanceOf(XmlDocument.Nfce.class, read(invoiceXml(KEY)));

		assertEquals(KEY, nfce.key().value());
		assertEquals(2, nfce.series());
		assertEquals(7, nfce.number());
		assertEquals(LocalDate.of(2026, 1, 15), nfce.date());
		assertEquals(new BigDecimal("10.50"), nfce.totalValue());
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
		return reader.read(xml.getBytes(StandardCharsets.UTF_8));
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
