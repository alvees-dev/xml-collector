package com.alveesdev.xmlcollector.application.settings;

import java.util.ArrayList;
import java.util.List;

import com.alveesdev.xmlcollector.application.port.SearchModelProvider;
import com.alveesdev.xmlcollector.domain.model.NfeSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvType;

public final class SearchModelCatalog {

	private final List<PdvSearchModel> pdvModels;
	private final List<NfeSearchModel> nfeModels;

	public SearchModelCatalog(SearchModelProvider provider) {
		this.pdvModels = List.copyOf(provider.pdvModels());
		this.nfeModels = List.copyOf(provider.nfeModels());
	}

	public List<PdvSearchModel> pdvModels(PdvType type) {
		return pdvModels.stream().filter(model -> model.type() == type).toList();
	}

	public List<NfeSearchModel> nfeModels() {
		List<NfeSearchModel> models = new ArrayList<>();
		models.add(NfeSearchModel.NONE);
		models.addAll(nfeModels);
		return models;
	}

	public PdvSearchModel pdvModel(String id) {
		return pdvModels.stream()
				.filter(model -> model.id().equals(id))
				.findFirst()
				.orElse(PdvSearchModel.NONE);
	}

	public NfeSearchModel nfeModel(String id) {
		return nfeModels.stream()
				.filter(model -> model.id().equals(id))
				.findFirst()
				.orElse(NfeSearchModel.NONE);
	}
}
