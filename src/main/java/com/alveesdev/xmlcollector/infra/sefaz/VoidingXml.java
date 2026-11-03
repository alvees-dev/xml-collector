package com.alveesdev.xmlcollector.infra.sefaz;

import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;
import com.alveesdev.xmlcollector.domain.model.VoidingRequest;

final class VoidingXml {

	static final String NFE_NAMESPACE = "http://www.portalfiscal.inf.br/nfe";
	static final String VERSION = "4.00";

	private static final String SERVICE = "INUTILIZAR";
	private static final String ID_ATTRIBUTE = "Id";

	private VoidingXml() {
	}

	static Document build(VoidingRequest request, FiscalEnvironment environment) throws GeneralSecurityException {

		Document document = newDocument();

		Element root = document.createElementNS(NFE_NAMESPACE, "inutNFe");
		root.setAttribute("versao", VERSION);
		document.appendChild(root);

		Element info = document.createElementNS(NFE_NAMESPACE, "infInut");
		info.setAttribute(ID_ATTRIBUTE, request.id());
		info.setIdAttribute(ID_ATTRIBUTE, true);
		root.appendChild(info);

		append(info, "tpAmb", environment.code());
		append(info, "xServ", SERVICE);
		append(info, "cUF", request.ufCode());
		append(info, "ano", request.shortYear());
		append(info, "CNPJ", request.cnpj());
		append(info, "mod", request.model().code());
		append(info, "serie", String.valueOf(request.series()));
		append(info, "nNFIni", String.valueOf(request.range().start()));
		append(info, "nNFFin", String.valueOf(request.range().end()));
		append(info, "xJust", request.justification());

		return document;
	}

	static void sign(Document document, A1Certificate certificate, String id) throws GeneralSecurityException {

		XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");

		try {
			Reference reference = factory.newReference(
					"#" + id,
					factory.newDigestMethod(DigestMethod.SHA1, null),
					List.of(
							factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
							factory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)),
					null,
					null);

			SignedInfo signedInfo = factory.newSignedInfo(
					factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
					factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
					List.of(reference));

			KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
			KeyInfo keyInfo = keyInfoFactory.newKeyInfo(
					List.of(keyInfoFactory.newX509Data(List.of(certificate.certificate()))));

			XMLSignature signature = factory.newXMLSignature(signedInfo, keyInfo);
			signature.sign(new DOMSignContext(certificate.privateKey(), document.getDocumentElement()));

		} catch (MarshalException | XMLSignatureException e) {
			throw new GeneralSecurityException("Não foi possível assinar o pedido: " + e.getMessage(), e);
		}
	}

	static String toText(Node node) throws GeneralSecurityException {
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

			Transformer transformer = factory.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.INDENT, "no");

			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(node), new StreamResult(writer));

			return writer.toString();

		} catch (TransformerException e) {
			throw new GeneralSecurityException("Não foi possível gerar o XML: " + e.getMessage(), e);
		}
	}

	private static Document newDocument() throws GeneralSecurityException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

			return factory.newDocumentBuilder().newDocument();

		} catch (ParserConfigurationException e) {
			throw new GeneralSecurityException("Não foi possível gerar o XML: " + e.getMessage(), e);
		}
	}

	private static void append(Element parent, String name, String value) {
		Element element = parent.getOwnerDocument().createElementNS(NFE_NAMESPACE, name);
		element.setTextContent(value);
		parent.appendChild(element);
	}
}
