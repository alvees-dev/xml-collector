package com.alveesdev.xmlcollector.ui.controller;

import java.util.HashMap;
import java.util.Map;

import com.alveesdev.xmlcollector.application.collection.CollectionProgress;
import com.alveesdev.xmlcollector.application.dispatch.DispatchProgress;
import com.alveesdev.xmlcollector.ui.model.DispatchProgressText;
import com.alveesdev.xmlcollector.ui.model.SourceProgressText;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ProgressController {

	private static final double STATUS_LABEL_WIDTH = 130;
	private static final String DONE_STYLE = "-fx-text-fill: #2e7d32; -fx-font-weight: bold;";
	private static final String WARNING_STYLE = "-fx-text-fill: #b26a00; -fx-font-weight: bold;";
	private static final String FAILED_STYLE = "-fx-text-fill: #c62828; -fx-font-weight: bold;";

	private record SourceRow(ProgressBar bar, Label status) {
	}

	@FXML
	private Label lblTitle;

	@FXML
	private VBox sourcesBox;

	@FXML
	private Button btnCancel;

	private final Map<Integer, SourceRow> rows = new HashMap<>();

	private Runnable cancelAction = () -> {
	};

	public void show(String title, Map<Integer, String> labels, double labelWidth) {
		lblTitle.setText(title);
		labels.forEach((index, label) -> addRow(index, label, labelWidth));
	}

	public void setCancelAction(Runnable cancelAction) {
		this.cancelAction = cancelAction;
	}

	public void update(CollectionProgress progress) {

		SourceRow row = rows.get(progress.sourceIndex());

		if (row == null) {
			return;
		}

		row.status().setText(SourceProgressText.of(progress));

		switch (progress.stage()) {
			case COUNTING -> row.bar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
			case READING -> row.bar().setProgress((double) progress.processed() / Math.max(progress.total(), 1));
			case DONE -> {
				row.bar().setProgress(1);
				row.status().setStyle(DONE_STYLE);
			}
			case DONE_WITH_WARNING -> {
				row.bar().setProgress(1);
				row.status().setStyle(WARNING_STYLE);
			}
			case FAILED -> {
				row.bar().setProgress(0);
				row.status().setStyle(FAILED_STYLE);
			}
		}
	}

	public void update(DispatchProgress progress) {

		SourceRow row = rows.get(progress.step().ordinal());

		row.status().setText(DispatchProgressText.of(progress.stage()));

		switch (progress.stage()) {
			case RUNNING -> row.bar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
			case DONE -> {
				row.bar().setProgress(1);
				row.status().setStyle(DONE_STYLE);
			}
			case SKIPPED -> row.bar().setProgress(0);
		}

		// The e-mail cannot be taken back, so it cannot be cancelled once it is on its way.
		if (progress.step() == DispatchProgress.Step.SENDING) {
			btnCancel.setDisable(true);
		}
	}

	public void showRunning() {
		ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
		bar.setMaxWidth(Double.MAX_VALUE);
		sourcesBox.getChildren().add(bar);
	}

	private void addRow(int index, String text, double labelWidth) {

		Label label = new Label(text);
		label.setMinWidth(labelWidth);

		ProgressBar bar = new ProgressBar(0);
		bar.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(bar, Priority.ALWAYS);

		Label status = new Label(SourceProgressText.WAITING);
		status.setMinWidth(STATUS_LABEL_WIDTH);

		HBox row = new HBox(8, label, bar, status);
		row.setAlignment(Pos.CENTER_LEFT);

		rows.put(index, new SourceRow(bar, status));
		sourcesBox.getChildren().add(row);
	}

	// The X of the window does what the button does, and nothing when the button can no longer be used.
	public void requestCancel() {
		if (!btnCancel.isDisable()) {
			onCancel();
		}
	}

	@FXML
	private void onCancel() {
		btnCancel.setDisable(true);
		btnCancel.setText("Cancelando...");
		cancelAction.run();
	}
}
