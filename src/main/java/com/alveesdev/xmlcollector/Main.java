package com.alveesdev.xmlcollector;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Ponto de entrada do programa. Só carrega a tela inicial (Scene1.fxml)
 * e mostra a janela — toda a lógica fica no Controller e nos pacotes
 * xmlconfig/model.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/alveesdev/xmlcollector/view/MainScreenScene1.fxml"));
        Scene scene = new Scene(loader.load());

        stage.setTitle("Coletor XML");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}