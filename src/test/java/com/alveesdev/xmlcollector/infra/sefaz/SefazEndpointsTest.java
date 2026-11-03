package com.alveesdev.xmlcollector.infra.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.FiscalEnvironment;

class SefazEndpointsTest {

	private static final List<String> ALL_UF_CODES = List.of(
			"11", "12", "13", "14", "15", "16", "17", "21", "22", "23", "24", "25", "26", "27",
			"28", "29", "31", "32", "33", "35", "41", "42", "43", "50", "51", "52", "53");

	@Test
	void everyStateHasDifferentProductionAndHomologationUrls() {
		for (String uf : ALL_UF_CODES) {
			assertNotEquals(
					SefazEndpoints.voidingUrl(uf, FiscalEnvironment.PRODUCTION),
					SefazEndpoints.voidingUrl(uf, FiscalEnvironment.HOMOLOGATION),
					uf);
		}
	}

	@Test
	void usesTheStateServiceWhenItHasOne() {
		assertEquals("homologacao.nfce.fazenda.sp.gov.br", SefazEndpoints.voidingUrl("35", FiscalEnvironment.HOMOLOGATION).getHost());
	}

	@Test
	void usesTheVirtualServiceForTheOtherStates() {
		assertEquals("nfce.svrs.rs.gov.br", SefazEndpoints.voidingUrl("33", FiscalEnvironment.PRODUCTION).getHost());
	}

	@Test
	void rejectsAnUnknownCode() {
		assertThrows(IllegalArgumentException.class, () -> SefazEndpoints.voidingUrl("99", FiscalEnvironment.HOMOLOGATION));
	}
}
