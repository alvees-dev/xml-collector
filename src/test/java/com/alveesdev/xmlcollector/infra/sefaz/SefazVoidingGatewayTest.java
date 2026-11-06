package com.alveesdev.xmlcollector.infra.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingNotSentException;
import com.alveesdev.xmlcollector.application.port.VoidingGatewayFactory;
import com.alveesdev.xmlcollector.application.settings.CertificateSettings;
import com.alveesdev.xmlcollector.domain.model.DocumentModel;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

class SefazVoidingGatewayTest {

	private static final char[] PASSWORD = "senha-falsa".toCharArray();
	private static final String DIGITAL_SIGNATURE_NAMESPACE = XMLSignature.XMLNS;
	private static final VoidingRequest REQUEST = new VoidingRequest(
			"35", 2026, "12345678000190", DocumentModel.NFCE, 1, new NumberRange(2, 3), "Numeracao nao utilizada & testada");

	@TempDir
	Path directory;

	private A1Certificate certificate;
	private FakeTransport transport;

	@BeforeEach
	void setUp() throws Exception {
		certificate = certificate("valid", "-1d", 30);
		transport = new FakeTransport();
	}

	@Test
	void buildsTheRequestInTheOrderOfTheSchema() throws Exception {
		Element info = (Element) parse(VoidingXml.toText(VoidingXml.build(REQUEST, FiscalEnvironment.HOMOLOGATION)))
				.getElementsByTagNameNS(VoidingXml.NFE_NAMESPACE, "infInut").item(0);

		List<String> names = new ArrayList<>();
		for (org.w3c.dom.Node node = info.getFirstChild(); node != null; node = node.getNextSibling()) {
			names.add(node.getLocalName() + "=" + node.getTextContent());
		}

		assertEquals(REQUEST.id(), info.getAttribute("Id"));
		assertEquals(List.of(
				"tpAmb=2", "xServ=INUTILIZAR", "cUF=35", "ano=26", "CNPJ=12345678000190", "mod=65",
				"serie=1", "nNFIni=2", "nNFFin=3", "xJust=Numeracao nao utilizada & testada"), names);
	}

	@Test
	void signsTheRequestWithAValidSignature() throws Exception {
		assertTrue(signatureIsValid(signedRequestSent()));
	}

	@Test
	void aChangeAfterSigningInvalidatesTheSignature() throws Exception {
		String tampered = signedRequestSent().replace("<nNFFin>3</nNFFin>", "<nNFFin>9</nNFFin>");

		assertFalse(signatureIsValid(tampered));
	}

	@Test
	void postsASoapEnvelopeToTheEndpointOfTheEnvironment() throws Exception {
		gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST);

		assertEquals("homologacao.nfce.fazenda.sp.gov.br", transport.url.getHost());
		assertTrue(transport.envelope.contains("http://www.w3.org/2003/05/soap-envelope"));
		assertTrue(transport.envelope.contains("<nfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4\">"));
	}

	@Test
	void readsAnApprovalAndBuildsTheProcessedXml() throws Exception {
		VoidingReceipt receipt = gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST);

		assertTrue(receipt.approved());
		assertEquals("135260000000001", receipt.protocol());
		assertEquals("Inutilizacao de numero homologado", receipt.reason());
		assertEquals("2026-02-05T10:00:00-03:00", receipt.receivedAt());

		Document processed = parse(receipt.xml());

		assertEquals("ProcInutNFe", processed.getDocumentElement().getLocalName());
		assertEquals(1, processed.getElementsByTagNameNS(VoidingXml.NFE_NAMESPACE, "inutNFe").getLength());
		assertEquals(1, processed.getElementsByTagNameNS(VoidingXml.NFE_NAMESPACE, "retInutNFe").getLength());
		assertEquals(1, processed.getElementsByTagNameNS(DIGITAL_SIGNATURE_NAMESPACE, "Signature").getLength());
	}

	@Test
	void readsARejection() throws Exception {
		transport.response = response("241", "Um numero da faixa ja foi utilizado", "");

		VoidingReceipt receipt = gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST);

		assertFalse(receipt.approved());
		assertEquals("241", receipt.status());
		assertEquals("Um numero da faixa ja foi utilizado", receipt.reason());
	}

	@Test
	void reportsAConnectionFailureAsUnconfirmed() {
		transport.failure = new IOException("timeout");

		VoidingException error = assertThrows(VoidingException.class, () -> gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST));

		assertTrue(error.getMessage().contains("timeout"));
		assertTrue(error.getMessage().contains("antes de tentar de novo"));
	}

	@Test
	void aSecureConnectionThatFailsSaysNothingWasSent() {
		transport.failure = new SSLHandshakeException("PKIX path building failed");

		VoidingException error = assertThrows(VoidingNotSentException.class, () -> gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST));

		assertTrue(error.getMessage().contains("PKIX path building failed"));
		assertTrue(error.getMessage().contains("Nada foi enviado"));
	}

	@Test
	void reportsAnUnexpectedResponse() {
		transport.response = "<soap:Envelope xmlns:soap=\"x\"><soap:Body><erro/></soap:Body></soap:Envelope>";
		assertThrows(VoidingException.class, () -> gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST));

		transport.response = "isto nao e xml";
		assertThrows(VoidingException.class, () -> gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST));
	}

	@Test
	void refusesAnExpiredCertificateWithoutSending() throws Exception {
		certificate = certificate("expired", "-10d", 1);

		VoidingException error = assertThrows(VoidingException.class, () -> gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST));

		assertTrue(error.getMessage().contains("vencido"));
		assertEquals(null, transport.envelope);
	}

	@Test
	void readsTheCnpjFromTheNameOfTheCertificate() {
		assertEquals(java.util.Optional.of("12345678000190"), certificate.cnpj());
	}

	@Test
	void theFactoryExplainsEachCertificateProblem() throws Exception {
		SefazVoidingGatewayFactory factory = new SefazVoidingGatewayFactory(Clock.systemUTC());
		Path valid = directory.resolve("valid.pfx");

		assertTrue(message(factory, new CertificateSettings("", "", FiscalEnvironment.HOMOLOGATION)).contains("Informe"));
		assertTrue(message(factory, new CertificateSettings(directory.resolve("ausente.pfx").toString(), "x", FiscalEnvironment.HOMOLOGATION)).contains("não encontrado"));
		assertTrue(message(factory, new CertificateSettings(valid.toString(), "errada", FiscalEnvironment.HOMOLOGATION)).contains("senha"));

		certificate("vencido", "-10d", 1);
		assertTrue(message(factory, new CertificateSettings(directory.resolve("vencido.pfx").toString(), new String(PASSWORD), FiscalEnvironment.HOMOLOGATION)).contains("vencido"));
	}

	@Test
	void theFactoryOpensAValidCertificateWithoutSendingAnything() throws Exception {
		VoidingGatewayFactory.Connection connection = new SefazVoidingGatewayFactory(Clock.systemUTC()).open(
				new CertificateSettings(directory.resolve("valid.pfx").toString(), new String(PASSWORD), FiscalEnvironment.HOMOLOGATION));

		assertEquals("12345678000190", connection.certificate().cnpj());
	}

	private static String message(SefazVoidingGatewayFactory factory, CertificateSettings settings) {
		return assertThrows(VoidingException.class, () -> factory.open(settings)).getMessage();
	}

	@Test
	void failsToLoadWithTheWrongPassword() throws Exception {
		certificate("wrong", "-1d", 30);

		assertThrows(IOException.class, () -> A1Certificate.load(directory.resolve("wrong.pfx"), "errada".toCharArray()));
	}

	private SefazVoidingGateway gateway(FiscalEnvironment environment) {
		return new SefazVoidingGateway(certificate, environment, transport, Clock.systemUTC());
	}

	private String signedRequestSent() throws Exception {
		gateway(FiscalEnvironment.HOMOLOGATION).voidRange(REQUEST);

		int start = transport.envelope.indexOf("<inutNFe");
		int end = transport.envelope.indexOf("</nfeDadosMsg>");

		return transport.envelope.substring(start, end);
	}

	private boolean signatureIsValid(String xml) throws Exception {
		Document document = parse(xml);
		Element info = (Element) document.getElementsByTagNameNS(VoidingXml.NFE_NAMESPACE, "infInut").item(0);
		info.setIdAttribute("Id", true);

		NodeList signatures = document.getElementsByTagNameNS(DIGITAL_SIGNATURE_NAMESPACE, "Signature");
		DOMValidateContext context = new DOMValidateContext(certificate.certificate().getPublicKey(), signatures.item(0));
		context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);

		return XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context);
	}

	private static Document parse(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	private A1Certificate certificate(String name, String start, int validityDays)
			throws IOException, InterruptedException, GeneralSecurityException {

		Path file = directory.resolve(name + ".pfx");

		Process process = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
				"-genkeypair", "-alias", "teste", "-keyalg", "RSA", "-keysize", "2048",
				"-dname", "CN=EMPRESA TESTE:12345678000190",
				"-startdate", start, "-validity", String.valueOf(validityDays),
				"-storetype", "PKCS12", "-keystore", file.toString(),
				"-storepass", new String(PASSWORD))
				.redirectErrorStream(true)
				.start();

		process.getInputStream().readAllBytes();
		assertTrue(process.waitFor(60, TimeUnit.SECONDS));
		assertEquals(0, process.exitValue());

		return A1Certificate.load(file, PASSWORD);
	}

	private static String response(String status, String reason, String protocol) {
		return "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>"
				+ "<nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4\">"
				+ "<retInutNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infInut>"
				+ "<tpAmb>2</tpAmb><cStat>" + status + "</cStat><xMotivo>" + reason + "</xMotivo><cUF>35</cUF>"
				+ "<dhRecbto>2026-02-05T10:00:00-03:00</dhRecbto>" + protocol
				+ "</infInut></retInutNFe></nfeResultMsg></soap:Body></soap:Envelope>";
	}

	private static class FakeTransport implements SoapTransport {

		private String response = SefazVoidingGatewayTest.response(
				"102", "Inutilizacao de numero homologado", "<nProt>135260000000001</nProt>");
		private IOException failure;
		private URI url;
		private String envelope;

		@Override
		public String post(URI url, String envelope) throws IOException {
			this.url = url;
			this.envelope = envelope;

			if (failure != null) {
				throw failure;
			}

			return response;
		}
	}
}
