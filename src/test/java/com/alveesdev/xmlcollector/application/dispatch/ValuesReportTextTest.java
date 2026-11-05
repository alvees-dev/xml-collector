package com.alveesdev.xmlcollector.application.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.domain.model.NumberRange;
import com.alveesdev.xmlcollector.domain.service.ValuesReport;

class ValuesReportTextTest {

	private static final ValuesReport.Section NFCE = new ValuesReport.Section("12345678000190", "65", 1, 100, 250,
			List.of(120, 130), List.of(new NumberRange(140, 142), new NumberRange(150, 150)), 142, new BigDecimal("12345.60"));

	@Test
	void writesEveryPartOfTheReport() {
		String text = ValuesReportText.textOf("Outubro de 2026", new ValuesReport(List.of(NFCE), 0));

		assertEquals(String.join("\r\n",
				"RELATÓRIO DE VALORES - Outubro de 2026",
				"",
				"CNPJ 12345678000190 - NFC-e - Série 1",
				"Primeira nota emitida: 100",
				"Última nota emitida: 250",
				"Notas canceladas: 120, 130",
				"Notas inutilizadas: 140 a 142, 150",
				"Total de vendas: 142 notas",
				"Valor total das vendas: R$ 12.345,60",
				"",
				"TOTAL DO MÊS",
				"Total de vendas: 142 notas",
				"Valor total das vendas: R$ 12.345,60",
				""), text);
	}

	@Test
	void showsWhatASeriesDoesNotHave() {
		ValuesReport.Section onlyVoided = new ValuesReport.Section("12345678000190", "55", 2, null, null, List.of(),
				List.of(), 1, BigDecimal.ZERO);

		String text = ValuesReportText.textOf("Outubro de 2026", new ValuesReport(List.of(onlyVoided), 0));

		assertTrue(text.contains("CNPJ 12345678000190 - NF-e - Série 2"), text);
		assertTrue(text.contains("Primeira nota emitida: -"), text);
		assertTrue(text.contains("Notas canceladas: Nenhuma"), text);
		assertTrue(text.contains("Notas inutilizadas: Nenhuma"), text);
		assertTrue(text.contains("Total de vendas: 1 nota\r\n"), text);
	}

	@Test
	void warnsAboutFilesThatCouldNotBeRead() {
		String text = ValuesReportText.textOf("Outubro de 2026", new ValuesReport(List.of(), 2));

		assertTrue(text.contains("ATENÇÃO: 2 arquivo(s) não puderam ser lidos"), text);
		assertTrue(text.contains("Nenhuma nota encontrada."), text);
	}

	@Test
	void theBytesStartWithTheMarkOfTheEncoding() {
		String decoded = new String(ValuesReportText.bytesOf("Outubro de 2026", new ValuesReport(List.of(), 0)), StandardCharsets.UTF_8);

		assertTrue(decoded.startsWith("\uFEFFRELATÓRIO"), decoded);
	}
}
