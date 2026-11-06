package com.alveesdev.xmlcollector.ui.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.alveesdev.xmlcollector.application.dispatch.DispatchResult;
import com.alveesdev.xmlcollector.application.dispatch.ReportOptions;
import com.alveesdev.xmlcollector.application.port.DispatchException;

public final class DispatchText {

	// The mail library splits the addresses only at commas; a semicolon stops the sending.
	public static final String RECIPIENTS_HELP = "Para enviar para mais de um e-mail, separe os endereços por vírgula."
			+ "\n\nExemplo: contador@exemplo.com, fiscal@exemplo.com"
			+ "\n\nNão use ponto e vírgula (;) nem só espaço entre os endereços: o envio não funciona.";

	private static final long MEGABYTE = 1024L * 1024;

	private DispatchText() {
	}

	public static String started(String monthLabel, String source, String recipient, boolean reuseExistingZip, ReportOptions reports) {

		List<String> names = new ArrayList<>();

		if (reports.nfce()) {
			names.add("NFCe");
		}

		if (reports.nfeSales()) {
			names.add("NFe Vendas");
		}

		if (reports.nfePurchases()) {
			names.add("NFe Compras");
		}

		return "Envio iniciado: XML de " + monthLabel + " do " + source + " para " + recipient
				+ (reuseExistingZip ? ", usando o arquivo compactado existente" : ", com um novo arquivo compactado")
				+ (names.isEmpty() ? ", sem relatório de valores" : ", relatório de valores: " + String.join(", ", names));
	}

	public static String success(String monthLabel, String recipient, DispatchResult result) {

		String sent = "O arquivo XML de " + monthLabel + " foi enviado para " + recipient + " com Sucesso!";

		if (result.method() == DispatchResult.Method.ATTACHED) {
			return sent;
		}

		String success = sent
				+ "\n\nEle passou do limite de 25 MB, então foi salvo no Google Drive e o e-mail levou o link da pasta."
				+ "\n\nLink: " + result.driveLink();

		if (result.driveCleanupFailure() == null) {
			return success;
		}

		return success + "\n\nAtenção: o arquivo anterior não foi removido da pasta e continua ocupando espaço. "
				+ "\nMotivo: " + result.driveCleanupFailure();
	}

	public static String compressed(String monthLabel, Path file, long bytes) {
		return "O arquivo XML de " + monthLabel + " foi compactado em:\n" + file
				+ "\n\nTamanho: " + sizeOf(bytes)
				+ "\n\nNada foi enviado.";
	}

	private static String sizeOf(long bytes) {
		Locale brazil = Locale.of("pt", "BR");

		if (bytes < MEGABYTE) {
			return String.format(brazil, "%d KB", Math.max(1, Math.round(bytes / 1024.0)));
		}

		return String.format(brazil, "%.1f MB", bytes / (double) MEGABYTE);
	}

	public static String failure(Throwable error) {
		if (error instanceof DispatchException) {
			return error.getMessage();
		}

		return "Erro inesperado ao enviar o e-mail: " + FailureText.of(error);
	}
}
