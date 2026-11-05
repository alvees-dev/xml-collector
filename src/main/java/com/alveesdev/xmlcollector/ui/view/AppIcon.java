package com.alveesdev.xmlcollector.ui.view;

import java.util.List;

import javafx.collections.ListChangeListener;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class AppIcon {

	static final List<String> RESOURCES = List.of(
			"/com/alveesdev/xmlcollector/icon/app-icon-16.png",
			"/com/alveesdev/xmlcollector/icon/app-icon-32.png",
			"/com/alveesdev/xmlcollector/icon/app-icon-48.png",
			"/com/alveesdev/xmlcollector/icon/app-icon-256.png");

	private AppIcon() {
	}

	// Alerts are created all over the program, so every window that opens gets the icon from here.
	public static void applyToEveryWindow() {
		List<Image> icons = RESOURCES.stream()
				.map(resource -> new Image(AppIcon.class.getResource(resource).toExternalForm()))
				.toList();

		Window.getWindows().forEach(window -> apply(window, icons));

		Window.getWindows().addListener((ListChangeListener<Window>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(window -> apply(window, icons));
			}
		});
	}

	private static void apply(Window window, List<Image> icons) {
		if (window instanceof Stage stage && stage.getIcons().isEmpty()) {
			stage.getIcons().setAll(icons);
		}
	}
}
