package com.alveesdev.xmlcollector.xmlconfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Document;

public class XmlProcessor {
	
	public static <T> Map<String, T> collectXml(
	        String directory, Function<Document, T> extractData) throws Exception {

	    Map<String, T> xmlList = new LinkedHashMap<>();

	    try (Stream<Path> xmlDirectory = Files.list(Paths.get(directory))) {
	    	
	        List<Path> xmls = xmlDirectory
	                .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
	                .collect(Collectors.toList());

	        for (Path xml : xmls) {
	            String fileName = xml.getFileName().toString();

	            try {
	                Document xmlFile = XmlReader.loadXML(xml.toFile());
	                xmlList.put(fileName, extractData.apply(xmlFile));
	            } catch (Exception e) {
	                System.err.println("Falha ao processar " + fileName + ": " + e.getMessage());
	                xmlList.put(fileName, null);
	            }
	        }
	    }

	    return xmlList;
	}

}
