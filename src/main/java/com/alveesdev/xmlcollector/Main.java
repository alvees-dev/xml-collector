package com.alveesdev.xmlcollector;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.bootstrap.AppContext;
import com.alveesdev.xmlcollector.ui.view.Dialogs;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        AppContext context = new AppContext(AppPaths.standard());
        AppPaths paths = context.paths();

        try {
            paths.ensureDirectories();
        } catch (IOException e) {
            Dialogs.error("Não foi possível criar as pastas do programa em:\n" + paths.root()
                    + "\n\nVerifique se o usuário do Windows tem permissão para criar pastas em C:\\.");
            Platform.exit();
            return;
        }

        try {
            context.issuedNumbers().initialize();
        } catch (IOException e) {
            Dialogs.warning(e.getMessage());
        }

        try {
            context.settings().load();
        } catch (IOException e) {
            Dialogs.warning("Não foi possível ler as configurações: " + e.getMessage());
        }

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/alveesdev/xmlcollector/view/MainScreen.fxml"));
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