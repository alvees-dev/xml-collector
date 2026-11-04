package com.alveesdev.xmlcollector.application.dispatch;

import java.nio.file.Path;
import java.util.List;

public record DispatchResult(Method method, List<Path> zipFiles, long totalZipBytes, String driveLink, String driveCleanupFailure) {

	public enum Method {
		ATTACHED,
		DRIVE_LINK
	}
}