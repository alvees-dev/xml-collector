package com.alveesdev.xmlcollector.infra.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alveesdev.xmlcollector.application.port.SourceFiles.ScanResult;
import com.alveesdev.xmlcollector.application.port.SourceFiles.SourceFile;

class XmlFileScannerScanTest {

	private final XmlFileScanner scanner = new XmlFileScanner();

	@TempDir
	Path root;

	private Path source;
	private Path archive;

	@BeforeEach
	void createFolders() throws IOException {
		source = Files.createDirectories(root.resolve("pdv"));
		archive = Files.createDirectories(root.resolve("arquivo"));
	}

	@Test
	void findsTheXmlFilesInEverySubfolder() throws IOException {
		Path inSource = write(source.resolve("a.xml"));
		Path inSubfolder = write(Files.createDirectories(source.resolve("2026").resolve("01")).resolve("b.xml"));

		ScanResult result = scanner.scan(source, archive);

		assertEquals(Set.of(inSource, inSubfolder), pathsOf(result.files()));
		assertEquals(List.of(), result.inaccessible());
	}

	@Test
	void theExtensionCanBeInCapitalLetters() throws IOException {
		Path capital = write(source.resolve("A.XML"));

		assertEquals(Set.of(capital), pathsOf(scanner.scan(source, archive).files()));
	}

	@Test
	void leavesOtherFilesOut() throws IOException {
		write(source.resolve("leia-me.txt"));
		write(source.resolve("a.xml.part"));
		write(source.resolve("xml"));

		assertEquals(Set.of(), pathsOf(scanner.scan(source, archive).files()));
	}

	@Test
	void keepsTheDateTheFileWasModified() throws IOException {
		Path file = write(source.resolve("a.xml"));
		FileTime modified = FileTime.from(Instant.parse("2026-01-15T12:00:00Z"));
		Files.setLastModifiedTime(file, modified);

		assertEquals(modified, scanner.scan(source, archive).files().get(0).lastModified());
	}

	@Test
	void readsADirectoryChosenInsideTheArchive() throws IOException {
		Path month = Files.createDirectories(archive.resolve("2026").resolve("Outubro_2026"));
		Path note = write(month.resolve("a.xml"));
		Path error = write(Files.createDirectories(month.resolve("Erros")).resolve("b.xml"));

		assertEquals(Set.of(note, error), pathsOf(scanner.scan(month, archive).files()));
	}

	@Test
	void readsTheArchiveItselfWhenItIsTheChosenDirectory() throws IOException {
		Path note = write(Files.createDirectories(archive.resolve("2026")).resolve("a.xml"));

		assertEquals(Set.of(note), pathsOf(scanner.scan(archive, archive).files()));
	}

	@Test
	void doesNotEnterTheArchiveWhenItIsInsideTheSource() throws IOException {
		Path kept = write(source.resolve("a.xml"));
		Path archiveInside = Files.createDirectories(source.resolve("arquivo"));
		write(Files.createDirectories(archiveInside.resolve("2026")).resolve("copia.xml"));

		assertEquals(Set.of(kept), pathsOf(scanner.scan(source, archiveInside).files()));
	}

	@Test
	void refusesAFolderThatDoesNotExist() {
		assertThrows(NotDirectoryException.class, () -> scanner.scan(source.resolve("nao-existe"), archive));
	}

	@Test
	void refusesAFileInPlaceOfAFolder() throws IOException {
		Path file = write(source.resolve("a.xml"));

		assertThrows(NotDirectoryException.class, () -> scanner.scan(file, archive));
	}

	private static Path write(Path file) throws IOException {
		return Files.writeString(file, "<xml/>");
	}

	private static Set<Path> pathsOf(List<SourceFile> files) {
		return files.stream().map(SourceFile::path).collect(Collectors.toSet());
	}
}
