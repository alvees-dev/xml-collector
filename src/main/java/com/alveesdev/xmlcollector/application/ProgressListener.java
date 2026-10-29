package com.alveesdev.xmlcollector.application;

@FunctionalInterface
public interface ProgressListener {

	ProgressListener NONE = progress -> {
	};

	void onProgress(CollectionProgress progress);
}