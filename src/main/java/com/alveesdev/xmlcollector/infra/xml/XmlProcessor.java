package com.alveesdev.xmlcollector.infra.xml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class XmlProcessor {

	public static List<Path> listXmlFiles(String directory, Path excludedRoot) throws IOException {

	    Path excluded = excludedRoot.toAbsolutePath().normalize();

	    try (Stream<Path> xmlDirectory = Files.walk(Paths.get(directory))) {
	        return xmlDirectory
	                .filter(Files::isRegularFile)
	                .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
	                .filter(p -> !p.toAbsolutePath().normalize().startsWith(excluded))
	                .collect(Collectors.toList());
	    }
	}

}