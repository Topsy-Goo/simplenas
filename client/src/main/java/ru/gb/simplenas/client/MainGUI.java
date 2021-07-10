package ru.gb.simplenas.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static ru.gb.simplenas.client.CFactory.STR_DEF_FOLDER;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.lnprint;


public class MainGUI extends Application
{
    private final int initialWidth = 800;
    private final int initialHeight = 600;
    private int minWidth = initialWidth;
    private int minHeight = initialHeight;
    private static final Logger LOGGER = LogManager.getLogger(MainGUI.class.getName());

    public static void main(String[] args)
    {
        LOGGER.info("------------------------------------ ");
        LOGGER.info("main(.) start");
        launch(args);
        LOGGER.info("main() end");
    }

    @Override public void start (Stage primaryStage) throws IOException
    {
    //инициализируем приложение
        FXMLLoader fxmlLoader = new FXMLLoader (getClass().getResource ("/window.fxml"));
        Parent root = fxmlLoader.load();

    //можно вот таким образом назначить обработчик закрытия пользователем окна приложения …:
        Controller controller = fxmlLoader.getController();
        primaryStage.setOnShowing (event -> controller.onMainWndShowing (primaryStage));
        primaryStage.setOnCloseRequest (event -> controller.closeSession());
        //… а можно это сделать из Controller'а (см.метод Controller.onCmdConnect).

    //продолжаем инициализацию
        primaryStage.setTitle (MAINWND_TITLE);
        Scene scene = new Scene (root, initialWidth, initialHeight);
        primaryStage.setScene (scene);
        primaryStage.show();
        primaryStage.setMinWidth (minWidth);
        primaryStage.setMinHeight (minHeight);
    }

}
