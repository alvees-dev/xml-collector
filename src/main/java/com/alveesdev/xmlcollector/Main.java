package com.alveesdev.xmlcollector;

import java.io.IOException;

import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.bootstrap.AppContext;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.TitleBar;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    private static final String APP_TITLE = "Coletor XML";

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

        VBox root = loader.load();
        root.getChildren().add(0, TitleBar.of(stage, APP_TITLE));

        Scene scene = new Scene(root);

        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}