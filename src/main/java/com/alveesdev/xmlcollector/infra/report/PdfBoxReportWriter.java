package com.alveesdev.xmlcollector.infra.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.alveesdev.xmlcollector.application.port.ReportPdfWriter;

// A plain A4 report: the title of each part larger, the headings in bold and the page count at the bottom.
public final class PdfBoxReportWriter implements ReportPdfWriter {

	private static final float MARGIN = 56;
	private static final float TITLE_SIZE = 14;
	private static final float HEADING_SIZE = 11;
	private static final float TEXT_SIZE = 10.5f;
	private static final float FOOTER_SIZE = 8;
	private static final float LEADING = 1.5f;
	private static final float[] TITLE_COLOR = {0.04f, 0.12f, 0.36f};
	private static final float[] FOOTER_COLOR = {0.45f, 0.45f, 0.45f};
	private static final String TITLE_START = "RELATÓRIO ";

	@Override
	public void write(Path file, List<Line> lines) throws IOException {

		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

		try (PDDocument document = new PDDocument()) {
			Writer writer = new Writer(document, regular, bold);

			for (Line line : lines) {
				writer.write(line);
			}

			writer.close();
			addPageNumbers(document, regular);

			Files.createDirectories(file.toAbsolutePath().getParent());
			document.save(file.toFile());
		}
	}

	private static void addPageNumbers(PDDocument document, PDType1Font font) throws IOException {

		int pages = document.getNumberOfPages();

		for (int index = 0; index < pages; index++) {
			PDPage page = document.getPage(index);

			try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
				String text = "Coletor XML - Página " + (index + 1) + " de " + pages;
				float width = font.getStringWidth(text) / 1000 * FOOTER_SIZE;

				content.setNonStrokingColor(FOOTER_COLOR[0], FOOTER_COLOR[1], FOOTER_COLOR[2]);
				content.beginText();
				content.setFont(font, FOOTER_SIZE);
				content.newLineAtOffset(page.getMediaBox().getWidth() - MARGIN - width, MARGIN / 2);
				content.showText(text);
				content.endText();
			}
		}
	}

	// Fills the pages top to bottom and opens a new one when the next line does not fit.
	private static final class Writer {

		private final PDDocument document;
		private final PDType1Font regular;
		private final PDType1Font bold;
		private PDPageContentStream content;
		private float y;

		Writer(PDDocument document, PDType1Font regular, PDType1Font bold) {
			this.document = document;
			this.regular = regular;
			this.bold = bold;
		}

		void write(Line line) throws IOException {

			boolean title = line.heading() && line.text().startsWith(TITLE_START);
			float size = title ? TITLE_SIZE : line.heading() ? HEADING_SIZE : TEXT_SIZE;
			float height = size * LEADING;

			if (content == null || y - height < MARGIN) {
				newPage();
			}

			y -= height;

			if (line.text().isEmpty()) {
				return;
			}

			PDType1Font font = line.heading() ? bold : regular;

			if (title) {
				content.setNonStrokingColor(TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2]);
			} else {
				content.setNonStrokingColor(0f, 0f, 0f);
			}

			content.beginText();
			content.setFont(font, size);
			content.newLineAtOffset(MARGIN, y);
			content.showText(printable(font, line.text()));
			content.endText();

			if (title) {
				content.setStrokingColor(TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2]);
				content.setLineWidth(0.8f);
				content.moveTo(MARGIN, y - 5);
				content.lineTo(PDRectangle.A4.getWidth() - MARGIN, y - 5);
				content.stroke();
				y -= 6;
			}
		}

		void close() throws IOException {
			if (content == null) {
				newPage();
			}
			content.close();
		}

		private void newPage() throws IOException {
			if (content != null) {
				content.close();
			}

			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			content = new PDPageContentStream(document, page);
			y = page.getMediaBox().getHeight() - MARGIN;
		}
	}

	// The standard fonts know the accents of Portuguese; anything else they cannot draw is left out instead of failing.
	static String printable(PDType1Font font, String text) {
		StringBuilder printable = new StringBuilder();

		for (int index = 0; index < text.length(); ) {
			int codePoint = text.codePointAt(index);
			String character = new String(Character.toChars(codePoint));

			try {
				font.encode(character);
				printable.append(character);
			} catch (IllegalArgumentException | IOException e) {
				// Not in the font.
			}

			index += Character.charCount(codePoint);
		}

		return printable.toString();
	}
}
