package com.alveesdev.xmlcollector.ui.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress;

public final class DispatchProgressText {

	private DispatchProgressText() {
	}

	public static Map<Integer, String> stepLabels() {
		Map<Integer, String> labels = new LinkedHashMap<>();

		for (DispatchProgress.Step step : DispatchProgress.Step.values()) {
			labels.put(step.ordinal(), label(step));
		}

		return labels;
	}

	public static String of(DispatchProgress.Stage stage) {
		return switch (stage) {
			case RUNNING -> "Em andamento...";
			case DONE -> "OK";
			case SKIPPED -> "Não necessário";
		};
	}

	private static String label(DispatchProgress.Step step) {
		return switch (step) {
			case COMPRESSING -> "Compactando arquivos";
			case UPLOADING -> "Salvando no Google Drive";
			case SENDING -> "Enviando e-mail";
		};
	}
}
