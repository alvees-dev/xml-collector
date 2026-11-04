package com.alveesdev.xmlcollector.ui.view;

import javafx.application.ColorScheme;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Window;

public final class LightWindows {

	private LightWindows() {
	}

	// The bar of a window follows the theme of Windows, and in the dark one it stands out from the colors of the program.
	public static void applyToEveryWindow() {
		Window.getWindows().forEach(LightWindows::apply);

		Window.getWindows().addListener((ListChangeListener<Window>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(LightWindows::apply);
			}
		});
	}

	private static void apply(Window window) {
		Scene scene = window.getScene();

		if (scene != null) {
			scene.getPreferences().setColorScheme(ColorScheme.LIGHT);
		}
	}
}
