package ru.gb.simplenas.server;

import com.sun.istack.internal.NotNull;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.server.services.ServerPropertyManager;
import ru.gb.simplenas.server.services.Server;
import ru.gb.simplenas.server.services.ServerFileManager;
import ru.gb.simplenas.server.services.impl.RemoteServer;
import ru.gb.simplenas.server.services.impl.RemoteFileManager;
import ru.gb.simplenas.server.services.impl.RemoteManipulator;
import ru.gb.simplenas.server.services.impl.RemotePropertyManager;

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

//-------------------------------------- RemoteServer --------------------------------------------------------------*/

/*  если название метода просто Server() плохо смтрится в main(), то название startServer()
    плохо смотрится в остальном коде, поэтому пусть будут два одинаковых метода с разными
    названиями. */
    public static Server startServer ()  {   return RemoteServer.getInstance();   }
    public static Server server()  {   return RemoteServer.getInstance();   }

//-------------------------------------- RemoteManipulator ---------------------------------------------------*/

    public static boolean clientsListAdd (RemoteManipulator manipulator, String userName)
    {
        return server().clientsListAdd(manipulator, userName);
    }

    public static void clientsListRemove (RemoteManipulator manipulator, String userName)
    {
        server().clientsListRemove(manipulator, userName);
    }

//-------------------------------------- NasServerPropertyManager -----------------------------------------------*/

    public static ServerPropertyManager getProperyManager ()
    {
        return RemotePropertyManager.getInstance();
    }

//--------------------------------- RemoteFileManager --------------------------------------------------------------*/

    public static ServerFileManager getServerFileManager (@NotNull String strCloud)
    {
        return new RemoteFileManager(strCloud);
    }

    public static ServerFileManager getServerFileManager (@NotNull String strCloud, @NotNull List<String> welcomeFolders, @NotNull List<String> welcomeFiles)
    {
        return new RemoteFileManager(strCloud, welcomeFolders, welcomeFiles);
    }

//---------------------------------- NasFileManager -------------------------------------------------------------*/

    public static boolean isNameValid (@NotNull String userName)
    {
        return NasFileManager.isNameValid(userName);
    }

    //public static boolean deleteFileOrDirectory (@NotNull Path path)
    //{
    //    return NasFileManager.deleteFileOrDirectory (path);
    //}

    //public static int countDirectoryEntries (@NotNull Path pFolder)
    //{
    //    return NasFileManager.countDirectoryEntries (pFolder);
    //}

    //public static FileInfo rename (@NotNull Path pParent, @NotNull String oldName, @NotNull String newName)
    //{
    //    return NasFileManager.rename (pParent, oldName, newName);
    //}

    //protected static Path createFolder (@NotNull Path pFolder)
    //{
    //    return NasFileManager.cre
    //}




















}
//---------------------------------------------------------------------------------------------------------------*/
