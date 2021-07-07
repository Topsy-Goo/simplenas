package ru.gb.simplenas.server;

import com.sun.istack.internal.NotNull;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.server.services.Server;
import ru.gb.simplenas.server.services.impl.NasServer;
import ru.gb.simplenas.server.services.impl.NasServerManipulator;
import ru.gb.simplenas.server.services.impl.ServerFileManager;
import ru.gb.simplenas.server.utils.impl.NasServerProperyManager;
import ru.gb.simplenas.server.utils.ServerProperyManager;

import java.nio.file.Path;
import java.util.List;

//
public class SFactory
{
    public static final String ERROR_INVALID_FILDER_SPECIFIED = "Указано некорректное имя папки.";
    public static final String ERR_FORMAT_LOGIN_REJECTED = "Авторизация отклонена. Возможно, пользователь уже подключен:\n\n%s";
    public static final String ERR_FORMAT_UNALLOWABLE_USERNAME = "Недопустимое имя пользователя:\n\n%s";
    public static final String ERROR_SERVER_UNABLE_TO_PERFORM = "Сервер не смог выполнить операцию!";

    private SFactory (){}

//-------------------------------------- NasServerManipulator ---------------------------------------------------*/

/*  если название метода просто Server() плохо смтрится в main(), то название startServer()
    плохо смотрится в остальном коде, поэтому пусть будут два одинаковых метода с разными
    названиями. */
    public static Server startSеrver ()  {   return NasServer.getInstance();   }
    public static Server server()  {   return NasServer.getInstance();   }

    public static boolean clientsListAdd (NasServerManipulator manipulator, String userName)
    {
        return server().clientsListAdd(manipulator, userName);
    }

    public static void clientsListRemove (NasServerManipulator manipulator, String userName)
    {
        server().clientsListRemove(manipulator, userName);
    }

//-------------------------------------- NasServerPropertyManager -----------------------------------------------*/

    public static ServerProperyManager nasProperyManager()  {   return NasServerProperyManager.getInstance();   }

    public static int getPortProperty()
    {
        return nasProperyManager().getPortProperty();
    }

    public static List<String> getWelcomeFileList()
    {
        return nasProperyManager().getWelcomeFileList();
    }

    public static List<String> getWelcomeDirsList()
    {
        return nasProperyManager().getWelcomeDirsList();
    }

//--------------------------------- ServerFileManager --------------------------------------------------------------*/

    public static boolean checkUserFolder (@NotNull String userName)
    {
        return ServerFileManager.checkUserFolder(userName);
    }

    public static Path constructAbsoluteUserRoot (@NotNull String userName)
    {
        return ServerFileManager.constructAbsoluteUserRoot(userName);
    }

    public static Path absolutePathToUserSpace (@NotNull String userName, @NotNull Path path, boolean mustBeFolder)
    {
        return ServerFileManager.absolutePathToUserSpace(userName, path, mustBeFolder);
    }

    public static FileInfo createSubfolder4User (@NotNull Path currentDir, @NotNull String userName, @NotNull String strNewDirName)
    {
        return ServerFileManager.createSubfolder4User(currentDir, userName, strNewDirName);
    }

    public static Path relativizeByUserName (@NotNull String userName, @NotNull Path path)
    {
        return ServerFileManager.relativizeByUserName(userName, path);
    }

    public static FileInfo safeRename (@NotNull Path pathParentAbsolute, @NotNull String oldName, @NotNull String newName, @NotNull String userName)
    {
        return ServerFileManager.safeRename(pathParentAbsolute, oldName, newName, userName);
    }

    public static boolean safeDeleteFileOrDirectory (@NotNull Path path, @NotNull String userNAme)
    {
        return ServerFileManager.safeDeleteFileOrDirectory(path, userNAme);
    }

    public static FileInfo getSafeFileInfo (@NotNull String userName, @NotNull String folder, @NotNull String file)
    {
        return ServerFileManager.getSafeFileInfo(userName, folder, file);
    }

    public static int safeCountDirectoryEntries (@NotNull Path folder, @NotNull String userName)
    {
        return ServerFileManager.safeCountDirectoryEntries(folder, userName);
    }

    public static boolean createCloudFolder (@NotNull String strCloudFolder)
    {
        return ServerFileManager.createCloudFolder(strCloudFolder);
    }

    public static boolean isUserNameValid (@NotNull String userName)
    {
        return ServerFileManager.isUserNameValid(userName);
    }


}
//---------------------------------------------------------------------------------------------------------------*/
