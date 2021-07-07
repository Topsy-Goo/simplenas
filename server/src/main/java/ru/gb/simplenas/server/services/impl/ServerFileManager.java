package ru.gb.simplenas.server.services.impl;

import com.sun.istack.internal.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.CommonData;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.common.structs.FileInfo;

import java.io.IOException;
import java.nio.file.*;

import static ru.gb.simplenas.common.CommonData.CLOUD;
import static ru.gb.simplenas.common.CommonData.MAX_USERNAME_LENGTH;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;

/*  Отличительной чертой этих методов и причиной выделения их в отдельный класс является то, что все они
    выполняют свои задачи, убедившись, что работают внутри дискового пространства пользователя (ДПП).
    Основу проверки составляет метод getSafeAbsolutePathBy(…), который в качестве параметра принимает
    имя корневой папки ДПП (фактически, — принимает логин пользователя).
*/
public class ServerFileManager extends NasFileManager
{
    private static final Logger LOGGER = LogManager.getLogger(ServerFileManager.class.getName());

    private ServerFileManager () {}

//вернёт FileInfo, только если указанный файл находится в дисковом пространстве юзера
    public static FileInfo getSafeFileInfo (@NotNull String userName, @NotNull String folder, @NotNull String file)   //ServerManipulator
    {
        FileInfo result = null;
        if (sayNoToEmptyStrings (userName, folder, file))
        {
            Path path = getSafeAbsolutePathBy (Paths.get(folder, file), userName);
            if (path != null)
            {
                result = new FileInfo (path);
            }
        }
        return result;
    }

    public static FileInfo createSubfolder4User (@NotNull Path pParent, @NotNull String userName, @NotNull String strChild)   //ServerManipulator
    {
        FileInfo result = null;
        if (pParent != null)
        {
            Path pChildAbsolute = getSafeAbsolutePathBy (pParent.resolve(strChild), userName);
            if (pChildAbsolute != null)
            {
                Path p = createFolder (pChildAbsolute);
                if (p != null)
                {
                    result = new FileInfo (p);
                }
            }
        }
        return result;
    }

    public static boolean createCloudFolder (@NotNull String strCloudFolder)    //ServerApp
    {
        return null != createFolder(CLOUD);
    }

//Создаём корневую папку дискового пространства нового пользователя (с подпапками).
    public static boolean checkUserFolder (@NotNull String userName)   //ServerManipulator
    {
        boolean result = false;
        Path userroot = constructAbsoluteUserRoot(userName);

        if (userroot != null)
        {
            if (!Files.exists(userroot) && createFolder (userroot) != null)
            try
            {   createNewUserFolders (userroot);
                createNewUserFiles (userroot);
            }
            catch (IOException e){e.printStackTrace();}
            result = Files.exists (userroot);
        }
        return result;
    }

    private static void createNewUserFolders (Path userroot) throws IOException     //fm
    {
        for (String s : CommonData.INITIAL_FOLDERS)    //< список стандартных папок
        {
            Path dir = userroot.resolve(s);
            createFolder (dir);
        }
    }

    private static void createNewUserFiles (Path user) throws IOException     //fm
    {
        for (String s : CommonData.INITIAL_FILES)      //< список стандартных файлов
        {
            Path pSrcFile = CLOUD.resolve(s);
            Path pTargetFile = user.resolve(s);

            if (Files.exists (pSrcFile))
            {
                if (!Files.exists (pTargetFile))
                {
                    Files.copy (pSrcFile, pTargetFile);
                }
            }
            else LOGGER.warn("createNewUserFiles(): !!!!! файл не найден : <"+pSrcFile.toString()+">");
        }
    }

//Из аргументов составляем такой абсолютный путь, который будет указывать внутрь дискового пространства пользователя.
    public static Path absolutePathToUserSpace (@NotNull String userName, @NotNull Path path, boolean mustBeFolder)     //ServerManipulator, ServerInboundFileExtruder
    {
        Path result = null;
        if (path != null)
        {
            Path tmp = getSafeAbsolutePathBy (path, userName);
            if (tmp != null  &&  mustBeFolder == Files.isDirectory (tmp))
            {
                result = tmp;
            }
        }
        return result;
    }

//Переименовываем файл или папку, если они находятся в дисковом пространстве пользователя (ДПП).
    public static FileInfo safeRename (@NotNull Path pParent, @NotNull String oldName, @NotNull String newName, @NotNull String userName)   //ServerManipulator,
    {
        FileInfo result = null;
        if ((pParent = getSafeAbsolutePathBy (pParent, userName)) != null)
        {
            result = rename (pParent, oldName, newName);
        }
        return result;
    }

//Вычисляем абсолютный путь к папке STRPATH_CLOUD\\userName.
    public static Path constructAbsoluteUserRoot (@NotNull String userName)     //fm, ServerManipulator, PathsTest
    {
        Path userroot = null;
        if (isUserNameValid (userName))
        {
            Path ptmp = Paths.get (userName);
            if (ptmp.getNameCount() == 1)
            {
                userroot = CLOUD.resolve(ptmp);
            }
        }
        return userroot;
    }

//разрешаем юзеру использовать только буквы и цыфры при указании логина.
    public static boolean isUserNameValid (@NotNull String userName)    //fm, ServerManipulator, PathsTest
    {
        boolean boolOk = false;
        if (sayNoToEmptyStrings(userName) && userName.length() <= MAX_USERNAME_LENGTH)
        {
            for (Character ch : userName.toCharArray())
            {
                boolOk = Character.isAlphabetic(ch) || Character.isDigit(ch);
                if (!boolOk)
                    break;
            }
        }
        return boolOk;
    }

//Считая, что путь path указывает в ДПП, составляем из него такой относительный путь, который начинается с userName.
    public static Path relativizeByUserName (@NotNull String userName, @NotNull Path path)      //ServerManipulator
    {
        Path result = null;
        if (path != null)
        {
            Path userroot = constructAbsoluteUserRoot (userName);
            if (userroot != null)
            {
                if (!path.isAbsolute())
                {
                    path = CLOUD.resolve(path);
                }
                path = path.normalize();

                if (path.startsWith (userroot))
                {
                    result = CLOUD.relativize(path);
                }
            }
        }
        return result;
    }

//возвращает количество элементов в указанном каталоге, если каталог принадлежит ДПП.
    public static int safeCountDirectoryEntries (@NotNull Path pFolder, @NotNull String userNAme)   //ServerManipulator
    {
        Path tmp = getSafeAbsolutePathBy (pFolder, userNAme);
        if (tmp != null)
        {
            return countDirectoryEntries (tmp);
        }
        return -1;
    }

//Удаляем файл или папку, если они находятся в ДПП.
    public static boolean safeDeleteFileOrDirectory (@NotNull Path path, @NotNull String userNAme) //ServerManipulator
    {
        Path tmp = getSafeAbsolutePathBy (path, userNAme);
        if (tmp != null)
        {
            return deleteFileOrDirectory (path);
        }
        return false;
    }

//Преобразуем path в абсолютный путь и убеждаемся, что он указывает в ДПП. Путь не обязан существовать.
    public static Path getSafeAbsolutePathBy (@NotNull Path path, @NotNull String userName)    //ServerFileManager, PathsTest
    {
        Path result = null,
             userroot = constructAbsoluteUserRoot(userName);

        if (path != null && userroot != null)
        {
            if (!path.isAbsolute())
            {
                path = CLOUD.resolve(path);  //< считаем, что path начинается с имени юзера
            }
            path = path.normalize();
            if (path.startsWith(userroot))
            {
                result = path;
            }
        }
        return result;
    }

//составляем относительный путь в дисковом пространстве пользователя userName.
    public static String safeRelativeParentStringFrom (@NotNull String userName, @NotNull String fromFolder)
    {
        String result = null;
        if (sayNoToEmptyStrings(userName, fromFolder))
        {
            Path userroot = constructAbsoluteUserRoot(userName);
            Path path = CLOUD.resolve(fromFolder).normalize().getParent();

            if (path.startsWith (userroot))
            {
                result = CLOUD.relativize(path).toString();
            }
        }
        return result;
    }


}
//---------------------------------------------------------------------------------------------------------------*/
//------------------------------ вызываются из клиента ----------------------------------------------------------*/


//------------------------------ вызываются из сервера ----------------------------------------------------------*/


//---------------------------------------------------------------------------------------------------------------*/

