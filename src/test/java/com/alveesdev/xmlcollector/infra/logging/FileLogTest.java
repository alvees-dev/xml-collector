package com.alveesdev.xmlcollector.infra.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLogTest {

	private static final Logger LOG = Logger.getLogger(FileLogTest.class.getName());

	@TempDir
	Path root;

	private Handler handler;

	@AfterEach
	void stopTheLog() {
		if (handler != null) {
			Logger.getLogger("").removeHandler(handler);
			handler.close();
		}
	}

	@Test
	void createsTheFolderAndWritesTheMessageWithItsTime() throws IOException {
		Path directory = root.resolve("Logs");
		handler = FileLog.start(directory);

		LOG.warning("Disco quase cheio");

		String log = logIn(directory);

		assertTrue(log.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} AVISO Disco quase cheio\\R"), log);
	}

	@Test
	void aDefectInTheProgramKeepsItsStack() throws IOException {
		handler = FileLog.start(root);

		LOG.log(Level.SEVERE, "A coleta falhou", new IllegalStateException("causa do erro"));

		String log = logIn(root);

		assertTrue(log.contains("A coleta falhou"), log);
		assertTrue(log.contains("java.lang.IllegalStateException: causa do erro"), log);
		assertTrue(log.contains("at com.alveesdev.xmlcollector"), log);
	}

	@Test
	void anExpectedFailureTakesASingleLineWithoutTheCode() throws IOException {
		handler = FileLog.start(root);

		LOG.log(Level.WARNING, "Arquivo não gravado", new IOException("Sem espaço\nno disco"));

		String log = logIn(root);

		assertTrue(log.endsWith("AVISO Arquivo não gravado | IOException: Sem espaço no disco" + System.lineSeparator()), log);
		assertFalse(log.contains("at com.alveesdev.xmlcollector"), log);
	}

	@Test
	void anExpectedFailureNamesEachOfItsCausesOnce() throws IOException {
		handler = FileLog.start(root);

		IOException cause = new IOException("Tempo esgotado");

		LOG.log(Level.WARNING, "Envio falhou", new IOException("Falha ao enviar", new IOException("Falha ao enviar", cause)));

		assertTrue(logIn(root).endsWith("Envio falhou | IOException: Falha ao enviar | IOException: Tempo esgotado" + System.lineSeparator()));
	}

	@Test
	void aDefectBehindAnExpectedFailureKeepsItsStack() throws IOException {
		handler = FileLog.start(root);

		LOG.log(Level.WARNING, "Envio falhou", new IOException("Falha ao enviar", new NullPointerException("nulo")));

		String log = logIn(root);

		assertTrue(log.contains("Caused by: java.lang.NullPointerException: nulo"), log);
		assertTrue(log.contains("at com.alveesdev.xmlcollector"), log);
	}

	@Test
	void writesTheAccentsInUtf8() throws IOException {
		handler = FileLog.start(root);

		LOG.warning("Não foi possível");

		assertTrue(logIn(root).contains("Não foi possível"));
	}

	@Test
	void doesNotRecordTheDetailsMeantForDebugging() throws IOException {
		handler = FileLog.start(root);

		LOG.fine("detalhe de depuracao");
		LOG.warning("aviso");

		assertFalse(logIn(root).contains("detalhe de depuracao"));
	}

	@Test
	void aPercentSignInTheFolderIsNotPartOfThePattern() throws IOException {
		Path directory = root.resolve("100%g");
		handler = FileLog.start(directory);

		LOG.warning("aviso");

		assertTrue(logIn(directory).contains("aviso"));
	}

	private String logIn(Path directory) throws IOException {
		handler.flush();

		return Files.readString(directory.resolve("coletor.0.log"), StandardCharsets.UTF_8);
	}
}
