package com.alveesdev.xmlcollector.ui.view;

import com.alveesdev.xmlcollector.ui.model.CollectionCounts;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

// The strip at the bottom of the main screen with how many notes the last collection or reading brought.
public final class StatusFooter extends HBox {

	private final Label action = new Label(CollectionCounts.NOTHING_YET);
	private final Label xml = new Label();
	private final Label cancelled = new Label();
	private final Label voided = new Label();
	private final Label errors = new Label();
	private final Label period = new Label();

	public StatusFooter() {
		super(16);
		getStyleClass().add("status-footer");
		setAlignment(Pos.CENTER_LEFT);
		setPadding(new Insets(4, 10, 4, 10));

		xml.getStyleClass().add("footer-number");
		errors.getStyleClass().add("footer-errors");

		Region space = new Region();
		HBox.setHgrow(space, Priority.ALWAYS);

		HBox done = new HBox(5, action, xml);
		done.setAlignment(Pos.CENTER_LEFT);

		getChildren().addAll(done, cancelled, voided, errors, space, period);
		for (Label count : new Label[] {xml, cancelled, voided, errors, period}) {
			setShown(count, false);
		}
	}

	public void show(CollectionCounts counts) {
		action.setText(counts.actionText());
		xml.setText(counts.xmlText());
		cancelled.setText(counts.cancelledText());
		voided.setText(counts.voidedText());
		errors.setText(counts.errorsText());
		period.setText(counts.periodText());

		for (Label label : new Label[] {xml, cancelled, voided, period}) {
			setShown(label, true);
		}

		setShown(errors, counts.errors() > 0);
	}

	private static void setShown(Label label, boolean shown) {
		label.setVisible(shown);
		label.setManaged(shown);
	}
}
