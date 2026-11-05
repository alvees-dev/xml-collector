package com.alveesdev.xmlcollector.endtoend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.alveesdev.xmlcollector.domain.model.NumberRange;

// Made-up documents with the structure of the real ones, because the repository is public and must never hold client files.
final class SyntheticXml {

	static final String CNPJ = "12345678000190";
	static final String UF = "35";
	static final String NFCE = "65";
	static final String VALUE_OF_A_NOTE = "10.50";

	private SyntheticXml() {
	}

	static void writeInvoice(Path folder, int series, int number, LocalDate date) throws IOException {
		Files.createDirectories(folder);
		Files.writeString(folder.resolve(keyOf(series, number, date) + "-nfe.xml"), invoice(series, number, date));
	}

	static void writeCancellation(Path folder, int series, int number, LocalDate date) throws IOException {
		String eventId = "110111" + keyOf(series, number, date) + "01";

		Files.createDirectories(folder);
		Files.writeString(folder.resolve(eventId + "-procEventoNFe.xml"), cancellation(eventId, series, number, date));
	}

	static void writeVoiding(Path folder, int series, NumberRange range, LocalDate date) throws IOException {
		String name = UF + shortYear(date) + CNPJ + NFCE + String.format("%03d%09d%09d", series, range.start(), range.end());

		Files.createDirectories(folder);
		Files.writeString(folder.resolve(name + "-procInutNFe.xml"), voiding(series, range, date));
	}

	static String keyOf(int series, int number, LocalDate date) {
		String yearMonth = shortYear(date) + String.format("%02d", date.getMonthValue());

		return UF + yearMonth + CNPJ + NFCE + String.format("%03d%09d1%09d", series, number, number);
	}

	static String voiding(int series, NumberRange range, LocalDate date) {
		return """
				<procInutNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
					<retInutNFe versao="4.00">
						<infInut>
							<tpAmb>1</tpAmb>
							<cStat>102</cStat>
							<xMotivo>Inutilizacao de numero homologado</xMotivo>
							<cUF>%s</cUF>
							<ano>%s</ano>
							<CNPJ>%s</CNPJ>
							<mod>%s</mod>
							<serie>%d</serie>
							<nNFIni>%d</nNFIni>
							<nNFFin>%d</nNFFin>
							<dhRecbto>%s</dhRecbto>
						</infInut>
					</retInutNFe>
				</procInutNFe>
				""".formatted(UF, shortYear(date), CNPJ, NFCE, series, range.start(), range.end(), atNoon(date));
	}

	private static String invoice(int series, int number, LocalDate date) {
		return """
				<nfeProc xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
					<NFe>
						<infNFe Id="NFe%s" versao="4.00">
							<ide>
								<serie>%d</serie>
								<nNF>%d</nNF>
								<dhEmi>%s</dhEmi>
							</ide>
							<total>
								<ICMSTot>
									<vNF>%s</vNF>
								</ICMSTot>
							</total>
						</infNFe>
					</NFe>
				</nfeProc>
				""".formatted(keyOf(series, number, date), series, number, atNoon(date), VALUE_OF_A_NOTE);
	}

	private static String cancellation(String eventId, int series, int number, LocalDate date) {
		return """
				<procEventoNFe xmlns="http://www.portalfiscal.inf.br/nfe" versao="1.00">
					<evento versao="1.00">
						<infEvento Id="ID%s">
							<chNFe>%s</chNFe>
							<dhEvento>%s</dhEvento>
							<tpEvento>110111</tpEvento>
						</infEvento>
					</evento>
				</procEventoNFe>
				""".formatted(eventId, keyOf(series, number, date), atNoon(date));
	}

	private static String shortYear(LocalDate date) {
		return String.format("%02d", date.getYear() % 100);
	}

	private static String atNoon(LocalDate date) {
		return date + "T12:00:00-03:00";
	}
}
