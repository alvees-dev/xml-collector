package com.alveesdev.xmlcollector.ui.view;

import java.awt.Desktop;
import java.net.URI;

import com.alveesdev.xmlcollector.ui.model.ActivityLog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class AboutWindow {

	public static final String SUMMARY = """
			O Coletor XML organiza os arquivos XML de NFC-e e NF-e da empresa.

			• Coleta os XMLs dos caixas (PDV) e do servidor e organiza por mês e por dia.
			• Encontra as numerações que faltam na sequência.
			• Inutiliza na Sefaz as numerações que não foram usadas.
			• Gera relatórios de valores em TXT e em PDF.
			• Compacta os XMLs do mês e envia por e-mail para a contabilidade.""";

	public static final String DEVELOPER = "Desenvolvido por Gabriel Alves";
	public static final String GITHUB = "https://github.com/alvees-dev";

	private static final String ICON = "/com/alveesdev/xmlcollector/icon/app-icon-48.png";

	private AboutWindow() {
	}

	public static void showAndWait(Window owner) {
		Stage stage = new Stage();

		ImageView icon = new ImageView(new Image(AboutWindow.class.getResource(ICON).toExternalForm()));
		Label title = new Label("Coletor XML");
		title.getStyleClass().add("progress-title");

		HBox heading = new HBox(12, icon, title);
		heading.setAlignment(Pos.CENTER_LEFT);

		Label summary = new Label(SUMMARY);
		summary.setWrapText(true);

		Label developer = new Label(DEVELOPER);

		Hyperlink github = new Hyperlink(GITHUB);
		github.setPadding(Insets.EMPTY);
		github.setOnAction(event -> open(GITHUB));

		VBox credits = new VBox(2, developer, github);

		Button close = new Button("Fechar");
		close.setDefaultButton(true);
		close.setCancelButton(true);
		close.setOnAction(event -> stage.close());

		HBox buttons = new HBox(close);
		buttons.setAlignment(Pos.CENTER_RIGHT);

		VBox root = new VBox(14, heading, summary, new Separator(), credits, buttons);
		root.setPadding(new Insets(20));
		root.setPrefWidth(480);
		root.getStylesheets().add(AboutWindow.class.getResource("/com/alveesdev/xmlcollector/view/app.css").toExternalForm());

		stage.setTitle("Sobre");
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(owner);
		stage.setResizable(false);
		stage.setScene(new Scene(root));
		stage.setOnShown(event -> close.requestFocus());
		stage.showAndWait();
	}

	// The browser of Windows opens on its own thread, so the window does not wait for it.
	private static void open(String link) {
		Thread.ofVirtual().name("xml-open-link").start(() -> {
			try {
				Desktop.getDesktop().browse(URI.create(link));
			} catch (Exception e) {
				ActivityLog.record("Não foi possível abrir o link " + link + ": " + e.getMessage());
			}
		});
	}
}
