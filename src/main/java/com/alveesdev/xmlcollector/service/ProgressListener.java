package com.alveesdev.xmlcollector.service;

@FunctionalInterface
public interface ProgressListener {

	ProgressListener NONE = progress -> {
	};

	void onProgress(CollectionProgress progress);
}