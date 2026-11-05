package com.alveesdev.xmlcollector.ui.view;

import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class TitleBar {

	private static final String STYLESHEET = "/com/alveesdev/xmlcollector/view/title-bar.css";
	private static final double HEIGHT = 32;
	private static final double ICON_SIZE = 16;

	private TitleBar() {
	}

	// Goes at the top of the window, with the style set before the stage is shown.
	public static HeaderBar of(Stage stage, String title) {

		stage.initStyle(StageStyle.EXTENDED);
		stage.setResizable(false);

		// Without this the system adds its own buttons, including the one to maximize.
		HeaderBar.setSystemButtonHeight(stage, 0);

		HeaderBar bar = new HeaderBar();
		bar.getStyleClass().add("title-bar");
		bar.getStylesheets().add(TitleBar.class.getResource(STYLESHEET).toExternalForm());
		bar.setMinHeight(HEIGHT);
		bar.setPrefHeight(HEIGHT);
		bar.setLeft(titleOf(stage, title));
		bar.setRight(new HBox(
				button("—", "title-bar-minimize", HeaderButtonType.ICONIFY),
				button("✕", "title-bar-close", HeaderButtonType.CLOSE)));

		return bar;
	}

	private static Node titleOf(Stage stage, String title) {

		ImageView icon = new ImageView();
		icon.setFitWidth(ICON_SIZE);
		icon.setFitHeight(ICON_SIZE);
		icon.setSmooth(true);

		// The icons of a window can arrive only when it opens.
		stage.getIcons().addListener((ListChangeListener<Image>) change -> showLastIcon(stage, icon));
		showLastIcon(stage, icon);

		HBox box = new HBox(8, icon, new Label(title));
		box.getStyleClass().add("title-bar-title");
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}

	private static void showLastIcon(Stage stage, ImageView icon) {
		icon.setImage(stage.getIcons().isEmpty() ? null : stage.getIcons().get(stage.getIcons().size() - 1));
	}

	private static Button button(String text, String styleClass, HeaderButtonType type) {
		Button button = new Button(text);
		button.getStyleClass().addAll("title-bar-button", styleClass);
		button.setFocusTraversable(false);
		button.setMaxHeight(Double.MAX_VALUE);
		HeaderBar.setButtonType(button, type);
		return button;
	}
}
