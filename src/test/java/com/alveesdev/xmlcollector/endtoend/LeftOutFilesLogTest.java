package com.alveesdev.xmlcollector.endtoend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.LogCapture;
import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionService;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.application.port.DocumentReader;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;
import com.alveesdev.xmlcollector.infra.archive.XmlArchive;
import com.alveesdev.xmlcollector.infra.persistence.SqliteIssuedNumberRepository;
import com.alveesdev.xmlcollector.infra.xml.FiscalXmlReader;
import com.alveesdev.xmlcollector.infra.xml.XmlFileScanner;

class LeftOutFilesLogTest {

	private static final DateRange JANUARY = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

	@TempDir
	Path root;

	@Test
	void saysWhichFileWasLeftOutAndWhy() throws IOException {
		Path pdv = Files.createDirectories(root.resolve("pdv1"));
		Files.writeString(pdv.resolve("quebrado.xml"), "<nfeProc><NFe>");
		Files.writeString(pdv.resolve("configuracao.xml"), "<configuracao/>");

		try (LogCapture log = new LogCapture(CollectionService.class)) {
			collect(pdv);

			assertTrue(log.has(Level.WARNING, "quebrado.xml"), "the unreadable file must be recorded");
			assertTrue(log.has(Level.INFO, "configuracao.xml"), "the ignored file must be recorded");
			assertTrue(log.has(Level.INFO, "não é nota"), "the reason must be recorded");
		}
	}

	private void collect(Path pdv) {
		DocumentReader reader = new FiscalXmlReader();
		CollectionService service = new CollectionService(new XmlFileScanner(), reader, new XmlArchive(reader),
				new SqliteIssuedNumberRepository(root.resolve("coletor.db")));

		service.collect(new CollectionRequest(List.of(pdv.toString()), DocumentCategory.NFCE, JANUARY, OrganizeMode.SINGLE_FOLDER, root.resolve("arquivo"),
				List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER), progress -> {
				}, () -> false);
	}
}
