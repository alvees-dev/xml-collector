package com.alveesdev.xmlcollector;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alveesdev.xmlcollector.application.settings.AppPaths;
import com.alveesdev.xmlcollector.bootstrap.AppContext;
import com.alveesdev.xmlcollector.infra.logging.FileLog;
import com.alveesdev.xmlcollector.ui.model.FailureText;
import com.alveesdev.xmlcollector.ui.view.AppIcon;
import com.alveesdev.xmlcollector.ui.view.Dialogs;
import com.alveesdev.xmlcollector.ui.view.LightWindows;
import com.alveesdev.xmlcollector.ui.view.TitleBar;
import com.alveesdev.xmlcollector.ui.view.UnexpectedErrorHandler;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final String APP_TITLE = "Coletor XML";

    @Override
    public void start(Stage stage) {
        AppPaths paths = AppPaths.standard();
        UnexpectedErrorHandler.install(paths.logsDir());

        try {
            open(stage, paths);
        } catch (Exception e) {
            // An error here would close the program without a word, because the packaged program has no console.
            LOG.log(Level.SEVERE, "O programa não pôde ser aberto", e);
            Dialogs.error(FailureText.unexpected(e, paths.logsDir()));
            Platform.exit();
        }
    }

    private void open(Stage stage, AppPaths paths) throws Exception {
        AppIcon.applyToEveryWindow();
        LightWindows.applyToEveryWindow();

        try {
            paths.ensureDirectories();
        } catch (IOException e) {
            Dialogs.error("Não foi possível criar as pastas do programa em:\n" + paths.root()
                    + "\n\nVerifique se o usuário do Windows tem permissão para criar pastas em C:\\.");
            Platform.exit();
            return;
        }

        startLog(paths);

        AppContext context = new AppContext(paths);

        try {
            context.issuedNumbers().initialize();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "O banco de histórico não pôde ser preparado", e);
            Dialogs.warning(e.getMessage());
        }

        try {
            context.settings().load();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "As configurações não puderam ser lidas", e);
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

    @Override
    public void stop() {
        LOG.info("Programa encerrado");
    }

    private static void startLog(AppPaths paths) {
        try {
            FileLog.start(paths.logsDir());
            LOG.info("Programa iniciado");
        } catch (IOException e) {
            // The program works without the file, and this message still reaches the console during development.
            LOG.log(Level.WARNING, "O arquivo de log não pôde ser aberto", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
