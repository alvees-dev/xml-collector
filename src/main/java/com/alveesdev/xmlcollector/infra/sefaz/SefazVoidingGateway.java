package com.alveesdev.xmlcollector.infra.sefaz;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;

import javax.net.ssl.SSLHandshakeException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.alveesdev.xmlcollector.application.port.VoidingException;
import com.alveesdev.xmlcollector.application.port.VoidingGateway;
import com.alveesdev.xmlcollector.application.port.VoidingNotSentException;
import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.VoidingReceipt;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;
import com.alveesdev.xmlcollector.infra.xml.XmlDocumentLoader;

public class SefazVoidingGateway implements VoidingGateway {

	private static final String SOAP_NAMESPACE = "http://www.w3.org/2003/05/soap-envelope";
	private static final String SERVICE_NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4";
	private static final String UNCONFIRMED_HINT = " Confira na Sefaz se a faixa foi inutilizada antes de tentar de novo.";

	private final A1Certificate certificate;
	private final FiscalEnvironment environment;
	private final SoapTransport transport;
	private final Clock clock;
	private final XmlDocumentLoader loader = new XmlDocumentLoader();

	public SefazVoidingGateway(A1Certificate certificate, FiscalEnvironment environment)
			throws GeneralSecurityException, IOException {
		this(certificate, environment, new HttpSoapTransport(certificate), Clock.systemUTC());
	}

	SefazVoidingGateway(A1Certificate certificate, FiscalEnvironment environment, SoapTransport transport, Clock clock) {
		this.certificate = certificate;
		this.environment = environment;
		this.transport = transport;
		this.clock = clock;
	}

	@Override
	public VoidingReceipt voidRange(VoidingRequest request) throws VoidingException {

		if (certificate.isExpired(clock.instant())) {
			throw new VoidingException("O certificado digital está vencido.");
		}

		String signedRequest = signedRequest(request);
		String response = send(SefazEndpoints.voidingUrl(request.ufCode(), environment), signedRequest);

		return parse(response, signedRequest);
	}

	private String signedRequest(VoidingRequest request) throws VoidingException {
		try {
			Document document = VoidingXml.build(request, environment);
			VoidingXml.sign(document, certificate, request.id());
			return VoidingXml.toText(document);
		} catch (GeneralSecurityException e) {
			throw new VoidingException(e.getMessage(), e);
		}
	}

	private String send(URI url, String signedRequest) throws VoidingException {

		String envelope = "<soap12:Envelope xmlns:soap12=\"" + SOAP_NAMESPACE + "\"><soap12:Body>"
				+ "<nfeDadosMsg xmlns=\"" + SERVICE_NAMESPACE + "\">" + signedRequest + "</nfeDadosMsg>"
				+ "</soap12:Body></soap12:Envelope>";

		try {
			return transport.post(url, envelope);
		} catch (IOException e) {
			if (failedBeforeSending(e)) {
				throw new VoidingNotSentException("Não foi possível conectar à Sefaz: " + e.getMessage() + ". Nada foi enviado, pode tentar de novo.", e);
			}

			throw new VoidingException("Falha ao comunicar com a Sefaz: " + e.getMessage() + "." + UNCONFIRMED_HINT, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new VoidingException("O envio foi interrompido." + UNCONFIRMED_HINT, e);
		}
	}

	// Without a connection, a secure channel or the address of the server, the request never left the computer.
	private static boolean failedBeforeSending(IOException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof SSLHandshakeException || cause instanceof ConnectException || cause instanceof UnknownHostException
					|| cause instanceof HttpConnectTimeoutException) {
				return true;
			}
		}

		return false;
	}

	private VoidingReceipt parse(String response, String signedRequest) throws VoidingException {

		Element result;

		try {
			result = firstElement(loader.load(response.getBytes(StandardCharsets.UTF_8)), "retInutNFe");
		} catch (IOException | SAXException e) {
			throw new VoidingException("A resposta da Sefaz não é um XML válido." + UNCONFIRMED_HINT, e);
		}

		if (result == null) {
			throw new VoidingException("A resposta da Sefaz não traz o resultado da inutilização." + UNCONFIRMED_HINT);
		}

		Element info = firstChild(result, "infInut");

		if (info == null || text(info, "cStat") == null) {
			throw new VoidingException("A resposta da Sefaz não traz o status da inutilização." + UNCONFIRMED_HINT);
		}

		if (!result.hasAttribute("xmlns")) {
			result.setAttribute("xmlns", VoidingXml.NFE_NAMESPACE);
		}

		return new VoidingReceipt(
				text(info, "cStat"),
				text(info, "xMotivo"),
				text(info, "nProt"),
				text(info, "dhRecbto"),
				processedXml(signedRequest, result));
	}

	private static String processedXml(String signedRequest, Element result) throws VoidingException {
		try {
			return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<ProcInutNFe versao=\"" + VoidingXml.VERSION + "\" xmlns=\"" + VoidingXml.NFE_NAMESPACE + "\">"
					+ signedRequest + VoidingXml.toText(result)
					+ "</ProcInutNFe>";
		} catch (GeneralSecurityException e) {
			throw new VoidingException(e.getMessage(), e);
		}
	}

	private static Element firstElement(Document document, String tag) {
		NodeList nodes = document.getElementsByTagName(tag);
		return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
	}

	private static Element firstChild(Element parent, String tag) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && tag.equals(element.getTagName())) {
				return element;
			}
		}
		return null;
	}

	private static String text(Element parent, String tag) {
		Element element = firstChild(parent, tag);
		return element == null ? null : element.getTextContent().trim();
	}
}
