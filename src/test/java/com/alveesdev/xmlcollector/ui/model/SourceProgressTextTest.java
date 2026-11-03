package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.collection.CollectionProgress;
import com.alveesdev.xmlcollector.application.collection.CollectionProgress.Stage;

class SourceProgressTextTest {

	@Test
	void describesEachStage() {
		assertEquals("Contando arquivos...", SourceProgressText.of(new CollectionProgress(0, Stage.COUNTING, 0, 0)));
		assertEquals("1.200 de 3.500", SourceProgressText.of(new CollectionProgress(0, Stage.READING, 1200, 3500)));
		assertEquals("OK - 3.500 arquivos", SourceProgressText.of(new CollectionProgress(0, Stage.DONE, 3500, 3500)));
		assertEquals("Falhou", SourceProgressText.of(new CollectionProgress(0, Stage.FAILED, 0, 0)));
	}

	@Test
	void usesTheSingularForOneFile() {
		assertEquals("OK - 1 arquivo", SourceProgressText.of(new CollectionProgress(0, Stage.DONE, 1, 1)));
	}
}
