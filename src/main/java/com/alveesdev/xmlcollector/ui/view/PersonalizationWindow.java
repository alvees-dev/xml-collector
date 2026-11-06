package com.alveesdev.xmlcollector.ui.view;

import java.util.List;
import java.util.Map;

import com.alveesdev.xmlcollector.application.settings.Theme;
import com.alveesdev.xmlcollector.ui.model.ActivityLog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class PersonalizationWindow {

	private static final String WINDOW_TITLE = "Personalização";
	private static final double SWATCH_SIZE = 18;

	// A sample of each theme in its own colors, whatever theme is in use.
	private static final Map<Theme, List<String>> SAMPLES = Map.of(
			Theme.DEEP_BLUE, List.of("#0a1f5c", "#1565ff", "#f4f7fc", "#d7e6ff"),
			Theme.NIGHT, List.of("#0a1430", "#13265a", "#1f6fff", "#3fc8ff"),
			Theme.DISCREET, List.of("#ffffff", "#f5f6f8", "#1565ff", "#c5cbd5"));

	private PersonalizationWindow() {
	}

	// The theme shows as soon as it is chosen, but only Salvar keeps it; otherwise the saved one comes back.
	public static void showAndWait(Window owner) {
		Stage stage = new Stage();
		Theme saved = Themes.current();
		boolean[] keep = {false};

		ToggleGroup group = new ToggleGroup();
		VBox options = new VBox(12);

		for (Theme theme : Theme.values()) {
			RadioButton option = new RadioButton(labelOf(theme));
			option.setUserData(theme);
			option.setToggleGroup(group);
			option.setSelected(theme == Themes.current());

			Region space = new Region();
			HBox.setHgrow(space, Priority.ALWAYS);

			HBox row = new HBox(12, option, space, samplesOf(theme));
			row.setAlignment(Pos.CENTER_LEFT);
			options.getChildren().add(row);
		}

		group.selectedToggleProperty().addListener((observable, oldOption, newOption) -> {
			if (newOption != null && newOption.getUserData() instanceof Theme theme) {
				Themes.preview(theme);
			}
		});

		Label title = new Label("Tema");
		title.getStyleClass().add("progress-title");

		Button save = new Button("Salvar");
		save.setDefaultButton(true);
		save.setOnAction(event -> {
			Theme chosen = Themes.current();

			if (chosen != saved) {
				ActivityLog.record("Tema alterado para " + chosen.label());
			}

			Themes.use(chosen);
			keep[0] = true;
			stage.close();
		});

		Button cancel = new Button("Cancelar");
		cancel.setCancelButton(true);
		cancel.setOnAction(event -> stage.close());

		// Closing by the X of the window is the same as Cancelar.
		stage.setOnHidden(event -> {
			if (!keep[0]) {
				Themes.preview(saved);
			}
		});

		HBox buttons = new HBox(8, save, cancel);
		buttons.setAlignment(Pos.CENTER_RIGHT);

		VBox root = new VBox(16, title, options, buttons);
		root.setPadding(new Insets(20));
		root.setPrefWidth(340);
		root.getStylesheets().add(PersonalizationWindow.class.getResource("/com/alveesdev/xmlcollector/view/app.css").toExternalForm());

		stage.setTitle(WINDOW_TITLE);
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(owner);
		stage.setResizable(false);
		stage.setScene(new Scene(root));
		stage.showAndWait();
	}

	private static String labelOf(Theme theme) {
		return switch (theme) {
			case DEEP_BLUE -> theme.label() + " (Claro)";
			case NIGHT -> theme.label() + " (Escuro)";
			case DISCREET -> theme.label();
		};
	}

	private static HBox samplesOf(Theme theme) {
		HBox samples = new HBox(4);

		for (String color : SAMPLES.get(theme)) {
			Rectangle swatch = new Rectangle(SWATCH_SIZE, SWATCH_SIZE, Color.web(color));
			swatch.setArcWidth(6);
			swatch.setArcHeight(6);
			swatch.setStroke(Color.web("#80808066"));
			samples.getChildren().add(swatch);
		}

		return samples;
	}
}
