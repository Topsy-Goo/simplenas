package ru.gb.simplenas.common.services.impl;

import com.sun.istack.internal.NotNull;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.CommonData;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ru.gb.simplenas.common.Factory.lnprint;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;


public class NasFileManager
{
    private static final Logger LOGGER = LogManager.getLogger(NasFileManager.class.getName());


    private NasFileManager() {}


// Заполняем структуру FileInfo fi данными, соответствующими файлу, на который указывает Path path.
    public static BasicFileAttributes readBasicFileAttributes2 (@NotNull Path path)    //+
    {
        if (path != null)
        {
            try
            {   BasicFileAttributeView view = Files.getFileAttributeView (path, BasicFileAttributeView.class);
                //Returns:  a file attribute view of the specified type,
                //          or null if the attribute view type is not available
                if (view != null)
                {
                    return view.readAttributes();
                }
            }
            catch (IOException e) {e.printStackTrace();}
        }
        return null;
    }

    private static @NotNull List<FileInfo> newFileInfoList()    {   return new ArrayList<>();   }    //+

//Составляем список файлов папки folderName.
    public static @NotNull List<FileInfo> listFolderContents (@NotNull String strFolderName)    //+
    {
        List<FileInfo> infolist = newFileInfoList();
        if (sayNoToEmptyStrings (strFolderName))
        {
            Path path = Paths.get (strFolderName);
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream (path);)
            {
                for(Path p : directoryStream)
                {
                    infolist.add (new FileInfo(p));
                }
            }
            catch (IOException | DirectoryIteratorException e) {e.printStackTrace();}
        }
        return infolist;
    }

    public static @NotNull List<FileInfo> listFolderContents (@NotNull Path pFolder)    //+
    {
        return (pFolder != null) ? listFolderContents (pFolder.toString())
                                 : newFileInfoList();
    }

//вернёт FileInfo только если указанный файл находится в дисковом пространстве юзера
    public static FileInfo getSafeFileInfo (@NotNull String userName, @NotNull String folder, @NotNull String file)    //+
    {
        FileInfo result = null;
        if (sayNoToEmptyStrings (userName, folder, file))
        {
            Path userroot = constructAbsoluteUserPath (userName);
            Path path = Paths.get(folder,file);

            if (!path.isAbsolute())
            {
                path = CommonData.CLOUD.resolve(path);
            }
            path = path.normalize();

            if (path.startsWith(userroot))
            {
                result = new FileInfo (path);
            }
        }
        return result;
    }

    public static boolean isStringOfRealPath (@NotNull String string, String ... strings)    //+
    {
        boolean boolYes = false;
        if (string != null)
        {
            try
            {   boolYes = Files.exists (Paths.get(string, strings));
            }
            catch (InvalidPathException e) { e.printStackTrace(); }
        }
        return boolYes;
    }

//Возвращает строку пути к родительской папке. Родительская папка должна существовать.
    public static @NotNull String stringPath2StringAbsoluteParentPath (@NotNull String strPath)    //+
    {
        String parent = "";
        if (strPath != null)
        {
            Path path = Paths.get(strPath).toAbsolutePath().normalize().getParent();
            if (path != null && Files.exists (path))
            {
                parent = path.toString();
            }
        }
        return parent;
    }

//Преобразуем размер файла в строку, удобную для отображения в свойтсвах файла в GUI.
    public static String fileSizeToString (long fsize)    //+
    {
        long Kilo = CommonData.FILESIZE_KILOBYTE;
        long Mega = Kilo * CommonData.FILESIZE_KILOBYTE;
        long Giga = Mega * CommonData.FILESIZE_KILOBYTE;
        long Tera = Giga * CommonData.FILESIZE_KILOBYTE;
        long r = fsize;
        String units = " байтов";

        if (fsize >= Kilo)
        {
            if (fsize < Giga)   {   r = fsize/Kilo;    units=" Кб";   }
            else
            if (fsize < Tera)   {   r = fsize/Mega;    units=" Мб";   }
            else                {   r = fsize/Tera;    units=" Гб";   }
        }
        return new DecimalFormat("###,###,###,###,###").format(r) + units;
    }

    public static String formatFileTime (long time)    //+
    {
        FileTime ft = FileTime.from(time, CommonData.filetimeUnits);
        LocalDateTime ldt = LocalDateTime.ofInstant (ft.toInstant(), ZoneId.systemDefault());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(CommonData.FILETIME_FORMAT_PATTERN, CommonData.RU_LOCALE);
        return ldt.format(dtf);
    }

    public static FileInfo createSubfolder4User (@NotNull Path pParent, @NotNull String userName, @NotNull String strChild)    //+
    {
        FileInfo result = null;
        if (pParent != null)
        {
            Path pChildAbsolute = pParent.toAbsolutePath().resolve(strChild).normalize();
            Path userroot = constructAbsoluteUserPath (userName);

            if (pChildAbsolute.startsWith (userroot))
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

    private static Path createFolder (@NotNull Path pFolder)    //+l
    {
        Path result = null;
        if (Files.exists (pFolder))
        {
            if (Files.isDirectory (pFolder))
            {
                result = pFolder;
            }
            else LOGGER.error("createFolder(): !!!!! Не удалось создать папку, — существует файл: <"+pFolder.toString()+">");
        }
        else
        {
            try
            {   result = Files.createDirectories (pFolder);
                LOGGER.trace("createFolder(): создана папка: "+ pFolder.toString());
            }
            catch (IOException e){e.printStackTrace();}
        }
        return result;
    }

// !!! Метод НЕ проверяет, находится ли strChild в STRPATH_CLOUD или в адресном пространстве юзера.
    public static Path createSubfolder (@NotNull Path pParent, @NotNull String strChild)    //+
    {
        Path result = null;
        if (pParent != null && sayNoToEmptyStrings (strChild))
        {
            Path pChild = pParent.toAbsolutePath().resolve(strChild).normalize();
            result = createFolder (pChild);
        }
        return result;
    }

    public static boolean createCloudFolder (@NotNull String strCloudFolder)    //+
    {
        return null != createFolder(CommonData.CLOUD);
    }

//Создаём корневую папку дискового пространства нового пользователя (с подпапками).
    public static boolean checkUserFolder (@NotNull String userName)    //+
    {
        boolean result = false;
        Path userroot = constructAbsoluteUserPath (userName);

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

    private static void createNewUserFolders (Path userroot) throws IOException    //+
    {
        for (String s : CommonData.INITIAL_FOLDERS)    //< список стандартных папок
        {
            Path dir = userroot.resolve(s);
            createFolder (dir);
        }
    }

    private static void createNewUserFiles (Path user) throws IOException    //+l
    {
        for (String s : CommonData.INITIAL_FILES)      //< список стандартных файлов
        {
            Path pSrcFile = CommonData.CLOUD.resolve(s);
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

//Из аргументов составляем такой абсолютный путь, который будет указывать внутрь дискового пространства пользователя userName.
//  Если path не содержит STRPATH_CLOUD\\userName, то будет возвращено умолчальное значение.
    public static Path absolutePathToUserSpace (@NotNull String userName, @NotNull Path path, boolean mustBeFolder)    //+
    {
        Path result = null;
        if (path != null)
        {
            Path userroot = constructAbsoluteUserPath (userName);
            if (userroot != null)
            {
                if (!path.isAbsolute())
                {
                    path = CommonData.CLOUD.resolve(path).normalize();
                }
                if (path.startsWith (userroot)  &&  mustBeFolder == Files.isDirectory (path))
                {
                    result = path;
                }
            }
        }
        return result;
    }

//добавляем к пути pParent ещё одно звено strPath, убедившись, что добавляется только одно звено.
    private static Path resolveFileNameAgainstPath (@NotNull Path pParent, @NotNull String strPath)    //+
    {
        if (strPath != null && pParent != null)
        {
            Path pFileName = Paths.get (strPath);
            if (pFileName.getNameCount() == 1)
            {
                return pParent.resolve(pFileName);
            }
        }
        return null;
    }

// !!! Метод НЕ проверяет, находится ли результат в дисковом пространстве пользователя (ДПП).
    public static FileInfo rename (@NotNull Path pParent, @NotNull String oldName, @NotNull String newName)    //+
    {
        FileInfo result = null;
        if (pParent != null && sayNoToEmptyStrings (oldName, newName))
        {
            Path pParentAbsolute = pParent.toAbsolutePath().normalize();
            Path pOlderSource = resolveFileNameAgainstPath (pParentAbsolute, oldName);
            Path pNewerTarget = resolveFileNameAgainstPath (pParentAbsolute, newName);

            if (pOlderSource != null && pNewerTarget != null
             && Files.exists (pOlderSource) && Files.notExists (pNewerTarget))
            {
                try
                {   Files.move (pOlderSource, pNewerTarget);
                    result = new FileInfo (pNewerTarget);
                    LOGGER.trace("rename(): переименование из\n\t\t<"+pOlderSource.toString()+">\n\t\tв <"+
                                 pNewerTarget.getFileName().toString()+">");
                }
                catch (IOException e){e.printStackTrace();}
            }
        }
        return result;
    }

//Вычисляем абсолютный путь к папке STRPATH_CLOUD\\userName.
    public static Path constructAbsoluteUserPath (@NotNull String userName)    //+
    {
        Path userroot = null;
        if (isUserNameValid (userName))
        {
            Path ptmp = Paths.get (userName);
            if (ptmp.getNameCount() == 1)
            {
                userroot = CommonData.CLOUD.resolve(ptmp);
            }
        }
        return userroot;
    }

//разрешаем юзеру использовать только буквы и цыфры при указании логина.
    public static boolean isUserNameValid (@NotNull String userName)    //+
    {
        boolean boolOk = false;
        if (sayNoToEmptyStrings(userName))
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
    public static Path relativizeByUserName (@NotNull String userName, @NotNull Path path)    //+
    {
        Path result = null;
        if (path != null)
        {
            Path userroot = constructAbsoluteUserPath (userName);
            if (userroot != null)
            {
                if (!path.isAbsolute())
                {
                    path = CommonData.CLOUD.resolve(path);
                }
                path = path.normalize();

                if (path.startsWith (userroot))
                {
                    result = CommonData.CLOUD.relativize(path);
                }
            }
        }
        return result;
    }

//составляет относительный путь в дисковом пространстве пользователя userName. Путь не обязан существовать.
    public static String safeRelativeLevelUpStringFrom (@NotNull String userName, @NotNull String fromFolder)    //+
    {
        String result = CommonData.STR_EMPTY;
        if (sayNoToEmptyStrings(userName, fromFolder))
        {
            Path userroot = constructAbsoluteUserPath (userName);
            Path p = CommonData.CLOUD.resolve(fromFolder).normalize().getParent();

            if (p.startsWith(userroot))
            {
                result = p.toString();
            }
            else
            {   result = userName;//CLOUD.relativize(userroot).toString();
            }
        }
        return result;
    }

    public static int countDirectoryEntries (Path folder)    //+
    {
        int result = -1;
        if (folder != null && Files.isDirectory(folder) && Files.exists(folder))
        {
            int[] counter = {0}; //< нужна effectively final-переменная
            try {
                    Files.newDirectoryStream (folder).forEach((p)->counter[0]++);
                    //Files.list(folder).forEach((p)->counter[0]++);    < это тоже работает
                    result = counter[0];
                }
            catch (IOException e){e.printStackTrace();}
        }
        return result;
    }

    public static boolean deleteFileOrDirectory (@NotNull Path path)    //+l
    {
        boolean result = false;
        if (path != null)
        {
            try
            {
                if (Files.isDirectory(path))
                {
                    FileUtils.deleteDirectory (path.toFile());
                    LOGGER.info("удалён каталог : <"+ path.toString() +">");
                    result = true;
                }
                else if (Files.isRegularFile(path))
                {
                    Files.delete (path);
                    LOGGER.info("удалён файл : <"+ path.toString() +">");
                    result = true;
                }
            }
            catch(IOException e){e.printStackTrace();}
        }
        return result;
    }


}
//---------------------------------------------------------------------------------------------------------------*/
