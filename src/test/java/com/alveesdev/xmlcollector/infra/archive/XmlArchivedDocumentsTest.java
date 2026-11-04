package com.alveesdev.xmlcollector.infra.archive;

import static com.alveesdev.xmlcollector.domain.TestDocuments.cancellation;
import static com.alveesdev.xmlcollector.domain.TestDocuments.invoice;
import static com.alveesdev.xmlcollector.domain.TestDocuments.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.domain.model.XmlDocument;

class XmlArchivedDocumentsTest {

	private static final LocalDate DAY = LocalDate.of(2026, 1, 15);
	private static final String KEY = key(1, 10).value();
	private static final String CNPJ = key(1, 10).cnpj();
	private static final String OTHER_KEY = KEY.substring(0, 6) + "98765432000110" + KEY.substring(20);

	@TempDir
	Path directory;

	@Test
	void takesTheCnpjFromTheKeyInTheFileNameWithoutOpeningIt() throws IOException {
		Files.writeString(directory.resolve(KEY + "-nfe.xml"), "invalido");

		XmlArchivedDocuments documents = new XmlArchivedDocuments((content, models) -> {
			throw new AssertionError("should not read the file");
		});

		assertEquals(Set.of(CNPJ), documents.cnpjsIn(directory));
	}

	@Test
	void readsTheInvoiceWhenTheNameHasNoKey() throws IOException {
		Files.writeString(directory.resolve("nota.xml"), "invoice");

		assertEquals(Set.of(CNPJ), documents().cnpjsIn(directory));
	}

	@Test
	void readsTheCancellationWhenTheNameHasNoKey() throws IOException {
		Files.writeString(directory.resolve("evento.xml"), "event");

		assertEquals(Set.of(CNPJ), documents().cnpjsIn(directory));
	}

	@Test
	void findsEveryCnpjInSubfolders() throws IOException {
		Path subfolder = Files.createDirectories(directory.resolve("NFCe"));
		Files.writeString(subfolder.resolve(KEY + ".xml"), "");
		Files.writeString(directory.resolve(OTHER_KEY + ".xml"), "");

		assertEquals(Set.of(CNPJ, "98765432000110"), documents().cnpjsIn(directory));
	}

	@Test
	void leavesTheSuppliersOfThePurchasesOut() throws IOException {
		Path sales = Files.createDirectories(directory.resolve("NFe_Vendas"));
		Path purchases = Files.createDirectories(directory.resolve("NFe_Compras"));
		Files.writeString(sales.resolve(KEY + ".xml"), "");
		Files.writeString(purchases.resolve(OTHER_KEY + ".xml"), "");

		assertEquals(Set.of(CNPJ), documents().cnpjsIn(directory));
	}

	@Test
	void ignoresWhatHasNoCnpj() throws IOException {
		Files.writeString(directory.resolve("ruim.xml"), "unreadable");
		Files.writeString(directory.resolve("leia-me.txt"), "invoice");

		assertEquals(Set.of(), documents().cnpjsIn(directory));
	}

	@Test
	void reportsAMissingFolder() {
		assertThrows(IOException.class, () -> documents().cnpjsIn(directory.resolve("ausente")));
	}

	private static XmlArchivedDocuments documents() {
		return new XmlArchivedDocuments((content, models) -> switch (new String(content, StandardCharsets.UTF_8)) {
			case "invoice" -> invoice(1, 10, DAY);
			case "event" -> cancellation(10, DAY);
			default -> new XmlDocument.Unreadable("invalido");
		});
	}
}
