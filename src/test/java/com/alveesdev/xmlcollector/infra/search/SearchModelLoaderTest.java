package com.alveesdev.xmlcollector.infra.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alveesdev.xmlcollector.application.port.SearchModelProvider;
import com.alveesdev.xmlcollector.application.settings.service.SearchModelCatalog;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvType;

class SearchModelLoaderTest {

	@Test
	void usesNoModelsWhenThePrivateClassesAreAbsent() {
		assertSame(SearchModelProvider.EMPTY, SearchModelLoader.load("com.alveesdev.xmlcollector.Absent"));
	}

	@Test
	void rejectsAClassThatIsNotAProvider() {
		assertThrows(IllegalStateException.class, () -> SearchModelLoader.load(String.class.getName() + "Builder"));
	}

	@Test
	void anUnknownModelMeansNoModel() {
		assertEquals(PdvSearchModel.NONE, new SearchModelCatalog(SearchModelProvider.EMPTY).pdvModel("removido"));
	}

	@Test
	void offersNenhumOnlyForNfe() {
		assertEquals(List.of(com.alveesdev.xmlcollector.domain.model.NfeSearchModel.NONE),
				new SearchModelCatalog(SearchModelProvider.EMPTY).nfeModels());
		assertEquals("Nenhum", com.alveesdev.xmlcollector.domain.model.NfeSearchModel.NONE.name());
	}

	@Test
	void listsOnlyTheModelsOfTheTypeWithoutAnEmptyOption() {
		PdvSearchModel food = new PdvSearchModel("f", "F", PdvType.FOOD, List.of("a"), List.of(), "", "");

		SearchModelCatalog catalog = new SearchModelCatalog(new SearchModelProvider() {

			@Override
			public List<PdvSearchModel> pdvModels() {
				return List.of(food);
			}

			@Override
			public List<com.alveesdev.xmlcollector.domain.model.NfeSearchModel> nfeModels() {
				return List.of();
			}
		});

		assertEquals(List.of(), catalog.pdvModels(PdvType.PDV));
		assertEquals(List.of(food), catalog.pdvModels(PdvType.FOOD));
	}
}
