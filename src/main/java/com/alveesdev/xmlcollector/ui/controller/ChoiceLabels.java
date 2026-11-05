package com.alveesdev.xmlcollector.ui.controller;

import java.util.function.Function;

import javafx.util.StringConverter;

final class ChoiceLabels {

	private ChoiceLabels() {
	}

	static <T> StringConverter<T> of(Function<T, String> label) {
		return new StringConverter<>() {

			@Override
			public String toString(T item) {
				return item == null ? "" : label.apply(item);
			}

			@Override
			public T fromString(String text) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
