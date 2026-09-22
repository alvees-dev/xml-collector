package com.gabriel.xmlconfig;

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
	        String diretorio, Function<Document, T> extrator) throws Exception {

	    Map<String, T> mapa = new LinkedHashMap<>();

	    try (Stream<Path> arquivos = Files.list(Paths.get(diretorio))) {
	        List<Path> xmls = arquivos
	                .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
	                .collect(Collectors.toList());

	        for (Path xml : xmls) {
	            String nomeArquivo = xml.getFileName().toString();

	            try {
	                Document doc = XmlReader.loadXML(xml.toFile());
	                mapa.put(nomeArquivo, extrator.apply(doc));
	            } catch (Exception e) {
	                System.err.println("Falha ao processar " + nomeArquivo + ": " + e.getMessage());
	                mapa.put(nomeArquivo, null);
	            }
	        }
	    }

	    return mapa;
	}

}
