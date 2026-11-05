package com.alveesdev.xmlcollector.ui.model;

import java.util.Locale;

import com.alveesdev.xmlcollector.application.dispatch.DispatchResult;
import com.alveesdev.xmlcollector.application.port.DispatchException;

public final class DispatchText {

	private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
	private static final double BYTES_PER_MEGABYTE = 1024.0 * 1024.0;

	private DispatchText() {
	}

	public static String success(String monthLabel, String recipient, DispatchResult result) {

		String size = String.format(BRAZIL, "%.1f MB", result.totalZipBytes() / BYTES_PER_MEGABYTE);

		if (result.method() == DispatchResult.Method.ATTACHED) {
			return "E-mail enviado para " + recipient + " com o XML de " + monthLabel
					+ " em anexo (" + size + ").";
		}

		String success = "O Arquivo XML de " + monthLabel + " (" + size + ") passou do limite de 25 MB e foi salvo no Google Drive."
				+ "\nO link para acessar a pasta foi enviado para " + recipient + "."
				+ "\n\nLink: " + result.driveLink();

		if (result.driveCleanupFailure() == null) {
			return success;
		}

		return success + "\n\nAtenção: o arquivo anterior não foi removido da pasta e continua ocupando espaço. "
				+ "\nMotivo: " + result.driveCleanupFailure();
	}

	public static String failure(Throwable error) {
		if (error instanceof DispatchException) {
			return error.getMessage();
		}

		return "Erro inesperado ao enviar o e-mail: " + FailureText.of(error);
	}
}
