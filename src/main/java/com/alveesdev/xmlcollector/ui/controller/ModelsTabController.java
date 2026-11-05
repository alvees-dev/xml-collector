package com.alveesdev.xmlcollector.ui.controller;

import java.util.List;
import java.util.stream.Stream;

import com.alveesdev.xmlcollector.application.connection.DirectoryTarget;
import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.application.settings.service.SearchModelCatalog;
import com.alveesdev.xmlcollector.domain.model.NfeSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvSearchModel;
import com.alveesdev.xmlcollector.domain.model.PdvType;
import com.alveesdev.xmlcollector.domain.model.SearchFolderPattern;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;

public class ModelsTabController {

	private final SearchModelCatalog searchModels;

	@FXML
	private ChoiceBox<PdvType> choicePdvType;

	@FXML
	private ChoiceBox<PdvSearchModel> choicePdvModel;

	@FXML
	private ChoiceBox<NfeSearchModel> choiceNfeModel;

	public ModelsTabController(SearchModelCatalog searchModels) {
		this.searchModels = searchModels;
	}

	void load(AppSettings settings) {
		choicePdvType.setConverter(ChoiceLabels.of(PdvType::label));
		choicePdvModel.setConverter(ChoiceLabels.of(PdvSearchModel::name));
		choiceNfeModel.setConverter(ChoiceLabels.of(NfeSearchModel::name));

		choicePdvType.getItems().setAll(PdvType.values());
		choicePdvType.setValue(settings.pdvType());
		showPdvModels(settings.pdvType(), settings.pdvModelId());

		choicePdvType.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldType, newType) -> showPdvModels(newType, PdvSearchModel.NONE.id()));

		List<NfeSearchModel> nfeModels = searchModels.nfeModels();
		choiceNfeModel.getItems().setAll(nfeModels);
		choiceNfeModel.setValue(nfeModels.stream()
				.filter(model -> model.id().equals(settings.nfeModelId()))
				.findFirst()
				.orElse(NfeSearchModel.NONE));
	}

	boolean pdvModelIsMissing() {
		return choicePdvModel.getValue() == null && !choicePdvModel.getItems().isEmpty();
	}

	PdvType pdvType() {
		return choicePdvType.getValue();
	}

	String pdvModelId() {
		PdvSearchModel model = choicePdvModel.getValue();

		return model == null ? PdvSearchModel.NONE.id() : model.id();
	}

	String nfeModelId() {
		return choiceNfeModel.getValue().id();
	}

	DirectoryTarget pdvTarget(String directory) {
		PdvSearchModel model = choicePdvModel.getValue();

		if (model == null) {
			return new DirectoryTarget(directory, List.of(), "");
		}

		List<String> folders = Stream.concat(model.collectionFolders().stream(), model.missingFolders().stream())
				.distinct()
				.toList();

		return new DirectoryTarget(directory, folders, SearchFolderPattern.fixedPrefix(model.voidingFolder()));
	}

	DirectoryTarget nfeTarget(String directory) {
		NfeSearchModel model = choiceNfeModel.getValue();

		List<String> folders = Stream.concat(model.salesFolders().stream(), model.purchaseFolders().stream())
				.distinct()
				.toList();

		return new DirectoryTarget(directory, folders, null);
	}

	private void showPdvModels(PdvType type, String selectedId) {
		List<PdvSearchModel> models = searchModels.pdvModels(type);

		choicePdvModel.getItems().setAll(models);
		choicePdvModel.setValue(models.stream()
				.filter(model -> model.id().equals(selectedId))
				.findFirst()
				.orElse(null));
	}
}
