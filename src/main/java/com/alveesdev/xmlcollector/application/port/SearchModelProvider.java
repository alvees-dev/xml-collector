package com.alveesdev.xmlcollector.application.port;

import java.util.List;

import com.alveesdev.xmlcollector.domain.model.NfeSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;

public interface SearchModelProvider {

	SearchModelProvider EMPTY = new SearchModelProvider() {

		@Override
		public List<PdvSearchModel> pdvModels() {
			return List.of();
		}

		@Override
		public List<NfeSearchModel> nfeModels() {
			return List.of();
		}
	};

	List<PdvSearchModel> pdvModels();

	List<NfeSearchModel> nfeModels();
}
