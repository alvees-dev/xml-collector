package com.alveesdev.xmlcollector.infra.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.application.port.ReportPdfWriter.Line;

class PdfBoxReportWriterTest {

	@TempDir
	Path directory;

	@Test
	void writesTheLinesWithTheirAccents() throws IOException {
		Path file = directory.resolve("Relatório Outubro 2026.pdf");

		new PdfBoxReportWriter().write(file, List.of(
				new Line("RELATÓRIO NFCe - Outubro de 2026", true),
				new Line("", false),
				new Line("Notas canceladas: 14 notas", false),
				new Line("Valor total das vendas: R$ 98.765,40", false)));

		try (PDDocument document = Loader.loadPDF(file.toFile())) {
			String text = new PDFTextStripper().getText(document);

			assertEquals(1, document.getNumberOfPages());
			assertTrue(text.contains("RELATÓRIO NFCe - Outubro de 2026"));
			assertTrue(text.contains("Notas canceladas: 14 notas"));
			assertTrue(text.contains("Página 1 de 1"));
		}
	}

	@Test
	void aLongReportGoesOnToMorePages() throws IOException {
		Path file = directory.resolve("longo.pdf");
		List<Line> lines = new ArrayList<>();

		for (int index = 0; index < 200; index++) {
			lines.add(new Line("Linha " + index, false));
		}

		new PdfBoxReportWriter().write(file, lines);

		try (PDDocument document = Loader.loadPDF(file.toFile())) {
			assertTrue(document.getNumberOfPages() > 1);
			assertTrue(new PDFTextStripper().getText(document).contains("Linha 199"));
		}
	}

	@Test
	void leavesOutWhatTheFontCannotDraw() throws IOException {
		assertEquals("Série 1 ok", PdfBoxReportWriter.printable(new org.apache.pdfbox.pdmodel.font.PDType1Font(
				org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), "Série 1 ok☃"));
	}
}
