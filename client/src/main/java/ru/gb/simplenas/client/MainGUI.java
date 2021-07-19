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
        LOGGER.debug("start() starts");
    //инициализируем приложение
        FXMLLoader fxmlLoader = new FXMLLoader (getClass().getResource ("/window.fxml"));   //LOGGER.debug("\n\t••\tFXMLLoader fxmlLoader = new FXMLLoader (getClass().getResource (\"/window.fxml\"));");
        //(Здесь выполняется Controller.initialize().)
        Parent root = fxmlLoader.load();                                                    //LOGGER.debug("\n\t••\tParent root = fxmlLoader.load();");

    //можно вот таким образом назначить обработчик закрытия пользователем окна приложения …:
        Controller controller = fxmlLoader.getController();                                 //LOGGER.debug("\n\t••\tController controller = fxmlLoader.getController();");
        primaryStage.setOnShowing (event -> controller.onMainWndShowing (primaryStage));    //LOGGER.debug("\n\t••\tprimaryStage.setOnShowing (event -> controller.onMainWndShowing (primaryStage));");
        primaryStage.setOnCloseRequest (event -> controller.closeSession());                //LOGGER.debug("\n\t••\tprimaryStage.setOnCloseRequest (event -> controller.closeSession());");
        //… а можно это сделать из Controller'а (см.метод Controller.onCmdConnect).

    //продолжаем инициализацию
        primaryStage.setTitle (MAINWND_TITLE);                                              //LOGGER.debug("\n\t••\tprimaryStage.setTitle (MAINWND_TITLE);");
        Scene scene = new Scene (root, initialWidth, initialHeight);                        //LOGGER.debug("\n\t••\tScene scene = new Scene (root, initialWidth, initialHeight); ");
        primaryStage.setScene (scene);                                                      //LOGGER.debug("\n\t••\tprimaryStage.setScene (scene);  ");
        //(Здесь выполняется листенер Controller.onMainWndShowing().)
        primaryStage.show();                                                                //LOGGER.debug("\n\t••\tprimaryStage.show();  ");
        primaryStage.setMinWidth (minWidth);                                                //LOGGER.debug("\n\t••\tprimaryStage.setMinWidth (minWidth);");
        primaryStage.setMinHeight (minHeight);                                              //LOGGER.debug("\n\t••\tprimaryStage.setMinHeight (minHeight);");
    }

}
