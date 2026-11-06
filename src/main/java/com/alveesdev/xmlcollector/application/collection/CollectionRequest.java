package com.alveesdev.xmlcollector.application.collection;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.alveesdev.xmlcollector.domain.model.DateRange;
import com.alveesdev.xmlcollector.domain.model.DocumentCategory;
import com.alveesdev.xmlcollector.domain.model.OrganizeMode;
import com.alveesdev.xmlcollector.domain.model.SeparationMode;

public record CollectionRequest(
		List<String> directories,
		DocumentCategory category,
		DateRange period,
		OrganizeMode organizeMode,
		Path archiveRoot,
		List<String> folderPatterns,
		CollectionTarget target,
		SeparationMode separationMode
) {

	// The first directory that lies inside the archive, where a collection would only copy the files onto themselves.
	public Optional<String> directoryInsideTheArchive() {
		Path archive = archiveRoot.toAbsolutePath().normalize();

		return directories.stream()
				.filter(directory -> directory != null && !directory.isBlank())
				.filter(directory -> isInside(directory.trim(), archive))
				.findFirst();
	}

	private static boolean isInside(String directory, Path archive) {
		try {
			return Path.of(directory).toAbsolutePath().normalize().startsWith(archive);
		} catch (InvalidPathException e) {
			return false;
		}
	}
}
