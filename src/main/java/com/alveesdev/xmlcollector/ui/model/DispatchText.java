package com.alveesdev.xmlcollector.ui.model;

import java.util.ArrayList;
import java.util.List;

import com.alveesdev.xmlcollector.application.dispatch.DispatchResult;
import com.alveesdev.xmlcollector.application.dispatch.ReportOptions;
import com.alveesdev.xmlcollector.application.port.DispatchException;

public final class DispatchText {

	private DispatchText() {
	}

	public static String started(String monthLabel, String recipient, boolean reuseExistingZip, ReportOptions reports) {

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

		return "Envio iniciado: XML de " + monthLabel + " para " + recipient
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

	public static String failure(Throwable error) {
		if (error instanceof DispatchException) {
			return error.getMessage();
		}

		return "Erro inesperado ao enviar o e-mail: " + FailureText.of(error);
	}
}
