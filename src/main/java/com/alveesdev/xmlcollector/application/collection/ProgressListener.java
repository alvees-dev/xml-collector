package com.alveesdev.xmlcollector.application.collection;

@FunctionalInterface
public interface ProgressListener {

	ProgressListener NONE = progress -> {
	};

	void onProgress(CollectionProgress progress);
}