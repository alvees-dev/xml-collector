package com.alveesdev.xmlcollector;

import java.util.Map;

import com.alveesdev.xmlcollector.xmlconfig.XmlExtractor;
import com.alveesdev.xmlcollector.xmlconfig.XmlProcessor;

public class Main {
	
	public static void main(String[] args) throws Exception {
        // Troque pelo caminho real do diretório com os XMLs
        String caminhoDiretorio = "C:\\Users\\Gabriel\\Desktop\\xml\\Dia_01";
 
        Map<String, String> resultado =
                XmlProcessor.collectXml(caminhoDiretorio, XmlExtractor::extrairNumeroNF);
 
        resultado.forEach((arquivo, numero) ->
                System.out.println(arquivo + " -> NF: " + numero));
    }
}