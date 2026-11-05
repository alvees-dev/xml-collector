package com.alveesdev.xmlcollector.ui.controller;

import com.alveesdev.xmlcollector.application.settings.AppSettings;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;

public class OrganizeTabController {

	@FXML
	private ChoiceBox<SeparationMode> choiceSeparation;

	@FXML
	private RadioButton radioSingleFolder;

	@FXML
	private RadioButton radioByDay;

	void load(AppSettings settings) {
		choiceSeparation.setConverter(ChoiceLabels.of(SeparationMode::label));
		choiceSeparation.getItems().setAll(SeparationMode.BY_PDV, SeparationMode.ALL_TOGETHER);
		choiceSeparation.setValue(settings.separationMode());

		if (settings.organizeMode() == OrganizeMode.BY_DAY) {
			radioByDay.setSelected(true);
		} else {
			radioSingleFolder.setSelected(true);
		}
	}

	OrganizeMode organizeMode() {
		return radioByDay.isSelected() ? OrganizeMode.BY_DAY : OrganizeMode.SINGLE_FOLDER;
	}

	SeparationMode separationMode() {
		return choiceSeparation.getValue();
	}
}
