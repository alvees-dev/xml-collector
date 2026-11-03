package com.alveesdev.xmlcollector.infra.search;

import com.alveesdev.xmlcollector.application.port.SearchModelProvider;

public final class SearchModelLoader {

	// Kept out of Git, so a fresh clone has no models and still compiles.
	private static final String PRIVATE_PROVIDER = "com.alveesdev.xmlcollector.infra.search.models.PrivateSearchModels";

	private SearchModelLoader() {
	}

	public static SearchModelProvider load() {
		return load(PRIVATE_PROVIDER);
	}

	static SearchModelProvider load(String className) {
		try {
			return (SearchModelProvider) Class.forName(className).getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException e) {
			return SearchModelProvider.EMPTY;
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new IllegalStateException("Invalid search model provider: " + className, e);
		}
	}
}
