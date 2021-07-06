package ru.gb.simplenas.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

import static ru.gb.simplenas.common.CommonData.MAINWND_TITLE;


public class MainGUI extends Application {
    private static final Logger LOGGER = LogManager.getLogger(MainGUI.class.getName());
    private final int initialWidth = 800;
    private final int initialHeight = 600;
    private final int minWidth = initialWidth;
    private final int minHeight = initialHeight;

    public static void main (String[] args) {
        LOGGER.fatal("------------------------------------ ");
        LOGGER.info("main(.) start");
        launch(args);
        LOGGER.info("main() end");
    }

    @Override public void start (Stage primaryStage) throws IOException {
        LOGGER.trace("start() start");
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/window.fxml"));
        Parent root = fxmlLoader.load();

        Controller controller = fxmlLoader.getController();
        primaryStage.setOnShowing(event->controller.onMainWndShowing(primaryStage));
        primaryStage.setOnCloseRequest(event->controller.closeSession());

        primaryStage.setTitle(MAINWND_TITLE);
        Scene scene = new Scene(root, initialWidth, initialHeight);
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setMinWidth(minWidth);
        primaryStage.setMinHeight(minHeight);
        LOGGER.trace("start() end");
    }

}
