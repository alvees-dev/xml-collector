package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.LogCapture;
import com.alveesdev.xmlcollector.application.collection.CollectionProgress;
import com.alveesdev.xmlcollector.application.collection.CollectionProgress.Stage;
import com.alveesdev.xmlcollector.application.collection.CollectionRequest;
import com.alveesdev.xmlcollector.application.collection.CollectionTarget;
import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;

class CollectionActivityTest {

	private static final CollectionRequest REQUEST = new CollectionRequest(
			List.of("\\\\pdv1", "\\\\pdv2"), DocumentCategory.NFCE, new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
			OrganizeMode.SINGLE_FOLDER, Path.of("arquivo"), List.of(), CollectionTarget.ALL, SeparationMode.ALL_TOGETHER);

	private final CollectionActivity activity = new CollectionActivity(REQUEST, SourceMode.PDV_DIRECTORIES);

	@Test
	void theStartSaysWhereFromAndThePeriod() {
		try (LogCapture log = new LogCapture(ActivityLog.class)) {
			activity.started("Coletando XML");

			assertEquals("Coletando XML por Diretório PDV, de 01/10/2026 a 31/10/2026", log.records().get(0).getMessage());
		}
	}

	@Test
	void tellsTheStepsOfEachSource() {
		try (LogCapture log = new LogCapture(ActivityLog.class)) {
			activity.record(new CollectionProgress(1, Stage.COUNTING, 0, 0));
			activity.record(new CollectionProgress(1, Stage.READING, 0, 9500));
			activity.record(new CollectionProgress(1, Stage.DONE, 9500, 9500));

			assertEquals(List.of(
					"PDV 02: procurando arquivos em \\\\pdv2",
					"PDV 02: 9500 arquivo(s) encontrado(s), coletando",
					"PDV 02: arquivos coletados"),
					log.records().stream().map(LogRecord::getMessage).toList());
		}
	}

	@Test
	void doesNotWriteALinePerFile() {
		try (LogCapture log = new LogCapture(ActivityLog.class)) {
			activity.record(new CollectionProgress(0, Stage.READING, 1, 9500));
			activity.record(new CollectionProgress(0, Stage.READING, 2, 9500));

			assertEquals(List.of(), log.records());
		}
	}

	@Test
	void aSourceWithProblemsIsAWarning() {
		try (LogCapture log = new LogCapture(ActivityLog.class)) {
			activity.record(new CollectionProgress(0, Stage.DONE_WITH_WARNING, 5, 5));
			activity.record(new CollectionProgress(1, Stage.FAILED, 0, 0));

			assertEquals(List.of(Level.WARNING, Level.WARNING), log.records().stream().map(LogRecord::getLevel).toList());
		}
	}
}
