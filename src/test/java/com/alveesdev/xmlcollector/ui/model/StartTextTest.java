package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class StartTextTest {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
	private static final LocalDate TO = LocalDate.of(2026, 10, 31);

	@Test
	void theCollectionSaysWhatWhereFromAndThePeriod() {
		assertEquals("Coletar: NFCe, NFe Vendas\nOrigem da NFCe: Diretório PDV\nPeríodo: 01/10/2026 a 31/10/2026",
				StartText.collection(List.of("NFCe", "NFe Vendas"), SourceMode.PDV_DIRECTORIES, FROM, TO));
	}

	@Test
	void onlyTheNfeHaveNoSourceOfNfce() {
		assertEquals("Coletar: NFe Compras\nPeríodo: 01/10/2026 a 31/10/2026",
				StartText.collection(List.of("NFe Compras"), null, FROM, TO));
	}

	@Test
	void theReadingSaysNothingIsCopied() {
		assertEquals("Ler: NFCe\nOrigem: Diretório Servidor\nPeríodo: 01/10/2026 a 31/10/2026\n\nNenhum arquivo será copiado.",
				StartText.reading(SourceMode.SERVER_DIRECTORY, null, FROM, TO));
	}

	@Test
	void anEmptyDiretorioUnicoSaysItReadsTheFolderOfTheProgram() {
		assertEquals("Ler: NFCe\nOrigem: Diretório Único\nDiretório: Arquivos_XML do Coletor\nPeríodo: 01/10/2026 a 31/10/2026\n\nNenhum arquivo será copiado.",
				StartText.reading(SourceMode.SINGLE_DIRECTORY, "Arquivos_XML do Coletor", FROM, TO));
	}
}
