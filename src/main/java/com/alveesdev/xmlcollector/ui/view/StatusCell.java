package com.alveesdev.xmlcollector.ui.view;

import com.alveesdev.xmlcollector.domain.model.DocumentStatus;
import com.alveesdev.xmlcollector.ui.model.DocumentRow;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;

// The status of a note as a small colored tag, so a cancelled note or one that could not be read stands out in the list.
public final class StatusCell extends TableCell<DocumentRow, String> {

	private final Label tag = new Label();

	@Override
	protected void updateItem(String text, boolean empty) {
		super.updateItem(text, empty);
		setText(null);

		DocumentRow row = getTableRow() == null ? null : getTableRow().getItem();

		if (empty || text == null || row == null) {
			setGraphic(null);
			return;
		}

		tag.setText(text);
		tag.getStyleClass().setAll("label", "status-tag", styleOf(row.getEntry().status()));
		setGraphic(tag);
	}

	private static String styleOf(DocumentStatus status) {
		return switch (status) {
			case AUTHORIZED -> "status-authorized";
			case CANCELLED -> "status-cancelled";
			case ERROR -> "status-error";
		};
	}
}
