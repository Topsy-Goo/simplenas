package ru.gb.simplenas.server;

import com.sun.istack.internal.NotNull;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.server.services.DbConnection;
import ru.gb.simplenas.server.services.ServerPropertyManager;
import ru.gb.simplenas.server.services.Server;
import ru.gb.simplenas.server.services.ServerFileManager;
import ru.gb.simplenas.server.services.impl.*;

import java.util.List;

public class SFactory {

    public static final int DEFAULT_PUBLIC_PORT_NUMBER = 8289;
    public static final String
        ERROR_INVALID_FOLDER_SPECIFIED = "Указано некорректное имя папки.",
        ERR_FORMAT_LOGIN_REJECTED       = "Авторизация отклонена. Возможно, пользователь уже подключен:\n\n%s",
        ERR_FORMAT_UNALLOWABLE_USERNAME = "Недопустимое имя пользователя:\n\n%s\n",
        ERROR_SERVER_UNABLE_TO_PERFORM  = "Сервер не смог выполнить операцию!",
        ERR_FORMAT_NOT_REGISTERED       = "Пользователь не зарегистрирован:\n\n%s\n",
    //public static final String ERROR_ALREADY_REGISTERED = "Повторная регистрация?";

        PROPERTY_FILE_NAME_SERVER     = "server.properties",   //< файл настроек (property file)
        DEFUALT_CLOUD_NAME            = "cloud",
        DEFUALT_WELCOM_FOLDERS_STRING = "documentes, pictures",
        PROPNAME_CLOUD_NAME           = "CLOUD.NAME",
        PROPNAME_PUBLIC_PORT          = "PUBLIC.PORT.NUMBER",
        PROPNAME_WELCOM_FOLDERS       = "WELCOME.FOLDERS",
        PROPNAME_WELCOM_FILES         = "WELCOME.FILES",

        CLASS_NAME   = "org.sqlite.JDBC",
        DATABASE_URL = "jdbc:sqlite:SimpleNAS.db",
        TABLE_NAME   = "simplenas_users";


    private SFactory () {}
//--------------------------------- RemoteServer ------------------------------------------

/*  если название метода просто Server() плохо смотрится в main(), то название startServer()
    плохо смотрится в остальном коде, поэтому пусть будут два одинаковых метода с разными
    названиями. */
    public static Server startServer () { return RemoteServer.getInstance(); }
    public static Server      server () { return startServer(); }

    public static boolean validateOnLogin (@NotNull String login, @NotNull String password) {
        return server().validateOnLogin(login, password);
    }
//--------------------------------- RemoteManipulator -------------------------------------

    public static boolean clientsListAdd (RemoteManipulator manipulator, String userName) {
        return server().clientsListAdd(manipulator, userName);
    }

    public static void clientRemove (RemoteManipulator manipulator, String userName) {
        server().clientRemove(manipulator, userName);
    }
//--------------------------------- NasServerPropertyManager ------------------------------

    public static ServerPropertyManager getProperyManager () { return RemotePropertyManager.getInstance(); }
//--------------------------------- RemoteFileManager -------------------------------------

    public static ServerFileManager getServerFileManager (@NotNull String strCloud) {
        return new RemoteFileManager(strCloud);
    }

    public static ServerFileManager getServerFileManager (
                        @NotNull String strCloud,
                        @NotNull List<String> welcomeFolders,
                        @NotNull List<String> welcomeFiles)
    {
        return new RemoteFileManager (strCloud, welcomeFolders, welcomeFiles);
    }
//--------------------------------- NasFileManager ----------------------------------------

    public static boolean isNameValid (@NotNull String userName) {
        return NasFileManager.isNameValid(userName);
    }
//---------------------------------- JdbcConnection ---------------------------------------
    public static DbConnection getDbConnection () { return JdbcConnection.getInstance(); }
}
