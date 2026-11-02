package com.alveesdev.xmlcollector.application.port;

import java.nio.file.Path;
import java.util.List;

public interface CloudStorage {

	String uploadToSharedFolder(String accountEmail, String folderName, List<Path> files) throws DispatchException;
}