package com.alveesdev.xmlcollector.application;

import java.nio.file.Path;
import java.util.List;

public record DispatchResult(Method method, List<Path> zipFiles, long totalZipBytes, String driveLink) {

	public enum Method {
		ATTACHED,
		DRIVE_LINK
	}
}