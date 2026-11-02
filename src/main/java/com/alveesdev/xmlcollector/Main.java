package com.alveesdev.xmlcollector;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.bootstrap.AppContext;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        AppContext context = new AppContext(AppPaths.standard());
        AppPaths paths = context.paths();

        try {
            paths.ensureDirectories();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR,
                    "Não foi possível criar as pastas do programa em:\n" + paths.root()
                            + "\n\nVerifique se o usuário do Windows tem permissão para criar pastas em C:\\.")
                    .showAndWait();
            Platform.exit();
            return;
        }

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/alveesdev/xmlcollector/view/MainScreenSceneV1.fxml"));
        loader.setControllerFactory(context::createController);

        Scene scene = new Scene(loader.load());

        stage.setTitle("Coletor XML");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}