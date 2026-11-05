package com.alveesdev.xmlcollector.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.collection.CollectionProgress.Stage;
import com.alveesdev.xmlcollector.application.collection.CollectionProgress;

class SourceProgressTextTest {

	@Test
	void describesEachStage() {
		assertEquals("Contando arquivos...", SourceProgressText.of(new CollectionProgress(0, Stage.COUNTING, 0, 0)));
		assertEquals("Arquivos no diretório: 1.200 de 3.500", SourceProgressText.of(new CollectionProgress(0, Stage.READING, 1200, 3500)));
		assertEquals("OK - Arquivos no diretório: 3.500", SourceProgressText.of(new CollectionProgress(0, Stage.DONE, 3500, 3500)));
		assertEquals("Falhou", SourceProgressText.of(new CollectionProgress(0, Stage.FAILED, 0, 0)));
	}

	@Test
	void describesAFinishWithWarning() {
		assertEquals("Aviso - Arquivos no diretório: 3.500", SourceProgressText.of(new CollectionProgress(0, Stage.DONE_WITH_WARNING, 3500, 3500)));
	}
}
