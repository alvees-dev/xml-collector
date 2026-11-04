package com.alveesdev.xmlcollector.ui.view;

import java.io.IOException;
import java.nio.file.Path;

public final class FileExplorer {

	private FileExplorer() {
	}

	// As a separate argument, because Explorer does not understand "/select" and a path with spaces quoted together.
	public static void showSelected(Path file) throws IOException {
		new ProcessBuilder("explorer.exe", "/select,", file.toAbsolutePath().toString()).start();
	}
}
