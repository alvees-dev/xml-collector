package com.alveesdev.xmlcollector.ui.view;

import java.net.URL;

import com.alveesdev.xmlcollector.application.settings.Theme;

import javafx.application.ColorScheme;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class Themes {

	private static final String FOLDER = "/com/alveesdev/xmlcollector/view/themes/";

	private static final ReadOnlyObjectWrapper<Theme> CURRENT = new ReadOnlyObjectWrapper<>(Theme.DEFAULT);

	private Themes() {
	}

	// Every window gets the theme when it opens, and the open ones change at once when the theme changes.
	public static void applyToEveryWindow(Theme theme) {
		CURRENT.set(theme);
		Window.getWindows().forEach(Themes::apply);

		Window.getWindows().addListener((ListChangeListener<Window>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(Themes::apply);
			}
		});

		CURRENT.addListener((observable, oldTheme, newTheme) -> Window.getWindows().forEach(Themes::apply));
	}

	public static void use(Theme theme) {
		CURRENT.set(theme);
	}

	public static Theme current() {
		return CURRENT.get();
	}

	public static ReadOnlyObjectProperty<Theme> currentProperty() {
		return CURRENT.getReadOnlyProperty();
	}

	public static URL stylesheetOf(Theme theme) {
		return Themes.class.getResource(FOLDER + fileOf(theme));
	}

	private static String fileOf(Theme theme) {
		return switch (theme) {
			case DEEP_BLUE -> "deep-blue.css";
			case NIGHT -> "night.css";
			case DISCREET -> "discreet.css";
		};
	}

	// The menus and the lists that drop down take the colors of the window they open from.
	private static void apply(Window window) {
		Scene scene = window.getScene();

		if (!(window instanceof Stage) || scene == null) {
			return;
		}

		Theme theme = CURRENT.get();
		scene.getStylesheets().removeIf(sheet -> sheet.contains(FOLDER));
		scene.getStylesheets().add(stylesheetOf(theme).toExternalForm());
		scene.getPreferences().setColorScheme(theme.isDark() ? ColorScheme.DARK : ColorScheme.LIGHT);
	}
}
