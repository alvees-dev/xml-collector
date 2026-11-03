package com.alveesdev.xmlcollector.infra.sefaz;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;

final class SefazEndpoints {

	private record Endpoint(String production, String homologation) {

		String url(FiscalEnvironment environment) {
			return environment == FiscalEnvironment.PRODUCTION ? production : homologation;
		}
	}

	private static final Endpoint SVRS = new Endpoint(
			"https://nfce.svrs.rs.gov.br/ws/nfeinutilizacao/nfeinutilizacao4.asmx",
			"https://nfce-homologacao.svrs.rs.gov.br/ws/nfeinutilizacao/nfeinutilizacao4.asmx");

	private static final Map<String, Endpoint> OWN_ENDPOINTS = Map.of(
			"13", new Endpoint(
					"https://nfce.sefaz.am.gov.br/nfce-services/services/NfeInutilizacao4",
					"https://homnfce.sefaz.am.gov.br/nfce-services-nac/services/NfeInutilizacao4"),
			"31", new Endpoint(
					"https://nfce.fazenda.mg.gov.br/nfce/services/NFeInutilizacao4",
					"https://hnfce.fazenda.mg.gov.br/nfce/services/NFeInutilizacao4"),
			"35", new Endpoint(
					"https://nfce.fazenda.sp.gov.br/ws/NFeInutilizacao4.asmx",
					"https://homologacao.nfce.fazenda.sp.gov.br/ws/NFeInutilizacao4.asmx"),
			"41", new Endpoint(
					"https://nfce.sefa.pr.gov.br/nfce/NFeInutilizacao4",
					"https://homologacao.nfce.sefa.pr.gov.br/nfce/NFeInutilizacao4"),
			"43", new Endpoint(
					"https://nfce.sefazrs.rs.gov.br/ws/nfeinutilizacao/nfeinutilizacao4.asmx",
					"https://nfce-homologacao.sefazrs.rs.gov.br/ws/nfeinutilizacao/nfeinutilizacao4.asmx"),
			"50", new Endpoint(
					"https://nfce.sefaz.ms.gov.br/ws/NFeInutilizacao4",
					"https://hom.nfce.sefaz.ms.gov.br/ws/NFeInutilizacao4"),
			"51", new Endpoint(
					"https://nfce.sefaz.mt.gov.br/nfcews/services/NfeInutilizacao4",
					"https://homologacao.sefaz.mt.gov.br/nfcews/services/NfeInutilizacao4"),
			"52", new Endpoint(
					"https://nfe.sefaz.go.gov.br/nfe/services/NFeInutilizacao4",
					"https://homolog.sefaz.go.gov.br/nfe/services/NFeInutilizacao4"));

	private static final Set<String> SVRS_UF_CODES = Set.of(
			"11", "12", "14", "15", "16", "17", "21", "22", "23", "24",
			"25", "26", "27", "28", "29", "32", "33", "42", "53");

	private SefazEndpoints() {
	}

	static URI voidingUrl(String ufCode, FiscalEnvironment environment) {

		Endpoint own = OWN_ENDPOINTS.get(ufCode);

		if (own != null) {
			return URI.create(own.url(environment));
		}

		if (SVRS_UF_CODES.contains(ufCode)) {
			return URI.create(SVRS.url(environment));
		}

		throw new IllegalArgumentException("Unknown UF code: " + ufCode);
	}
}
