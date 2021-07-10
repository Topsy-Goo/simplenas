package ru.gb.simplenas.server;

import com.sun.istack.internal.NotNull;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.server.services.PropertyManager;
import ru.gb.simplenas.server.services.Server;
import ru.gb.simplenas.server.services.ServerFileManager;
import ru.gb.simplenas.server.services.impl.NasServer;
import ru.gb.simplenas.server.services.impl.NasServerFileManager;
import ru.gb.simplenas.server.services.impl.NasServerManipulator;
import ru.gb.simplenas.server.services.impl.ServerPropertyManager;

import java.nio.file.Path;
import java.util.List;

public class SFactory
{
    public static final String ERROR_INVALID_FILDER_SPECIFIED = "Указано некорректное имя папки.";
    public static final String ERR_FORMAT_LOGIN_REJECTED = "Авторизация отклонена. Возможно, пользователь уже подключен:\n\n%s";
    public static final String ERR_FORMAT_UNALLOWABLE_USERNAME = "Недопустимое имя пользователя:\n\n%s";
    public static final String ERROR_SERVER_UNABLE_TO_PERFORM = "Сервер не смог выполнить операцию!";

    public static final String PROPERTY_FILE_NAME_SERVER = "server.properties";   //< файл настроек (property file)
    public static final int DEFAULT_PUBLIC_PORT_NUMBER = 8289;
    public static final String DEFUALT_CLOUD_NAME = "cloud";
    public static final String DEFUALT_WELCOM_FOLDERS_STRING = "documentes, pictures";
    public static final String PROPNAME_CLOUD_NAME = "CLOUD.NAME";
    public static final String PROPNAME_PUBLIC_PORT = "PUBLIC.PORT.NUMBER";
    public static final String PROPNAME_WELCOM_FOLDERS = "WELCOME.FOLDERS";
    public static final String PROPNAME_WELCOM_FILES = "WELCOME.FILES";

    private SFactory (){}

//-------------------------------------- NasServer --------------------------------------------------------------*/

/*  если название метода просто Server() плохо смтрится в main(), то название startServer()
    плохо смотрится в остальном коде, поэтому пусть будут два одинаковых метода с разными
    названиями. */
    public static Server startServer ()  {   return NasServer.getInstance();   }
    public static Server server()  {   return NasServer.getInstance();   }

//-------------------------------------- NasServerManipulator ---------------------------------------------------*/

    public static boolean clientsListAdd (NasServerManipulator manipulator, String userName)
    {
        return server().clientsListAdd(manipulator, userName);
    }

    public static void clientsListRemove (NasServerManipulator manipulator, String userName)
    {
        server().clientsListRemove(manipulator, userName);
    }

//-------------------------------------- NasServerPropertyManager -----------------------------------------------*/

    public static PropertyManager getProperyManager ()
    {
        return ServerPropertyManager.getInstance();
    }

//--------------------------------- NasServerFileManager --------------------------------------------------------------*/

    public static ServerFileManager getServerFileManager (@NotNull String strCloud)
    {
        return new NasServerFileManager (strCloud);
    }

    public static ServerFileManager getServerFileManager (@NotNull String strCloud, @NotNull List<String> welcomeFolders, @NotNull List<String> welcomeFiles)
    {
        return new NasServerFileManager (strCloud, welcomeFolders, welcomeFiles);
    }

//---------------------------------- NasFileManager -------------------------------------------------------------*/

    public static boolean isNameValid (@NotNull String userName)
    {
        return NasFileManager.isNameValid(userName);
    }


}
//---------------------------------------------------------------------------------------------------------------*/
