package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.LogCapture;

class ActivityLogTest {

	@Test
	void anActivityIsInformationByDefault() {
		try (LogCapture log = new LogCapture(ActivityLog.class)) {
			ActivityLog.record("Abriu configurações");

			LogRecord entry = log.records().get(0);

			assertEquals(Level.INFO, entry.getLevel());
			assertEquals("Abriu configurações", entry.getMessage());
		}
	}

	@Test
	void keepsTheLevelItWasGiven() {
		try (LogCapture log = new LogCapture(ActivityLog.class)) {
			ActivityLog.record(Level.WARNING, "Conferir certificado: Informe o certificado.");

			assertEquals(Level.WARNING, log.records().get(0).getLevel());
		}
	}

	@Test
	void aTextOfManyLinesTakesOneLine() {
		try (LogCapture log = new LogCapture(ActivityLog.class)) {
			ActivityLog.record("Coleta concluída: Copiados: 10\nJá existiam: 2\n\nArquivos salvos em: pasta\n");

			assertEquals("Coleta concluída: Copiados: 10 | Já existiam: 2 | Arquivos salvos em: pasta", log.records().get(0).getMessage());
		}
	}
}
