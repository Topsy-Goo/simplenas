package ru.gb.simplenas.common.services.impl;

import com.sun.istack.internal.NotNull;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.structs.FileInfo;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static ru.gb.simplenas.common.CommonData.MAX_USERNAME_LENGTH;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;

/*  Класс-родитель для классов ClientFileManager и ServerFileManager
*/
public class NasFileManager
{
    protected NasFileManager () {}

    private static final Logger LOGGER = LogManager.getLogger(NasFileManager.class.getName());

    protected static Path createFolder (@NotNull Path pFolder)    //fm
    {
        Path result = null;
        if (pFolder != null)
        if (Files.exists(pFolder))
        {
            if (Files.isDirectory (pFolder))
            {
                result = pFolder;
            }
            else LOGGER.error("createFolder(): !!!!! Не удалось создать папку, — существует файл: <"+pFolder.toString()+">");
        }
        else
        {   try
            {   result = Files.createDirectories (pFolder);
                LOGGER.trace("createFolder(): создана папка: "+ pFolder.toString());
            }
            catch (IOException e){e.printStackTrace();}
        }
        return result;
    }

    public static FileInfo rename (@NotNull Path pParent, @NotNull String oldName, @NotNull String newName)     //TableViewManager
    {
        FileInfo result = null;
        if (pParent != null && sayNoToEmptyStrings (oldName, newName))
        {
            Path pParentAbsolute = pParent.toAbsolutePath().normalize();
            Path pOlderSource = resolveFileNameAgainstPath (pParentAbsolute, oldName);
            Path pNewerTarget = resolveFileNameAgainstPath (pParentAbsolute, newName);

            if (pOlderSource != null  &&  pNewerTarget != null
             &&  Files.exists (pOlderSource)  &&  Files.notExists (pNewerTarget))
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

//добавляем к пути pParent ещё одно звено strPath, убедившись, что добавляется только одно звено.
    protected static Path resolveFileNameAgainstPath (@NotNull Path pParent, @NotNull String strPath)     //fm
    {
        if (strPath != null && pParent != null)
        {
            Path pFileName = Paths.get(strPath);
            if (pFileName.getNameCount() == 1)
            {
                return pParent.resolve(pFileName);
            }
        }
        return null;
    }

//укорачиваем относительный путь на одно звено, если есть такая возможность. Путь не обязан существовать.
    public static String getParentFromRelative (@NotNull String strRoot, @NotNull String strFromFolder)    //NetClient
    {
        String result = null;
        if (sayNoToEmptyStrings (strRoot, strFromFolder))
        {
            Path pFrom = Paths.get(strFromFolder).normalize();
            if (pFrom.getNameCount() <= 2)
            {
                result = strRoot;
            }
            else
            {
                result = pFrom.getParent().toString();
            }
        }
        return result;
    }

    public static int countDirectoryEntries (@NotNull Path pFolder)       //Controller
    {
        int result = -1;
        if (pFolder != null && Files.isDirectory(pFolder) && Files.exists(pFolder))
        {
            int[] counter = {0}; //< нужна effectively final-переменная
            try {
                    Files.newDirectoryStream (pFolder).forEach((p)->counter[0]++);
                    //Files.list(folder).forEach((p)->counter[0]++);    < это тоже работает
                    result = counter[0];
                }
            catch (IOException e){e.printStackTrace();}
        }
        return result;
    }

    public static boolean deleteFileOrDirectory (@NotNull Path path)     //ServerManipulator, Controller
    {
        boolean result = false;
        if (path != null)
        {
            try
            {
                if (Files.isDirectory(path))
                {
                    FileUtils.deleteDirectory(path.toFile());
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

// Заполняем структуру FileInfo fi данными, соответствующими файлу, на который указывает Path path.
    public static BasicFileAttributes readBasicFileAttributes2 (@NotNull Path path)     //FileInfo,
    {
        if (path != null)
        {
            try
            {   BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class);
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

//Составляем список файлов папки strFolderName.
    public static @NotNull List<FileInfo> listFolderContents (@NotNull String strFolderName)    //fm, Controller
    {
        return (sayNoToEmptyStrings (strFolderName)) ? listFolderContents (Paths.get (strFolderName))
                                                     : newFileInfoList();
    }

    protected static @NotNull List<FileInfo> newFileInfoList()    {   return new ArrayList<>();   }

    public static @NotNull List<FileInfo> listFolderContents (@NotNull Path pFolder)    //ServerManipulator
    {
        List<FileInfo> infolist = newFileInfoList();
        if (pFolder != null)
        {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pFolder);)
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

//разрешаем юзеру использовать только буквы и цыфры при указании логина. (Предполагаем, что буквы и цифры разрешены
// в именах папок на всех платформах.)
    public static boolean isNameValid (@NotNull String userName)    //fm, ServerManipulator, PathsTest, ServerProperyManager
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

    public static boolean createCloudFolder (@NotNull Path pCloudFolder)    //ServerProperyManager
    {
        return null != createFolder (pCloudFolder);
    }


}
