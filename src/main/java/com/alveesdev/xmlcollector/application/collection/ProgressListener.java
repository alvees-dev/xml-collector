package com.alveesdev.xmlcollector.application.collection;

@FunctionalInterface
public interface ProgressListener {

	void onProgress(CollectionProgress progress);
}