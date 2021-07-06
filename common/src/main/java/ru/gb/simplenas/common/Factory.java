package ru.gb.simplenas.common;

import com.sun.istack.internal.NotNull;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Factory
{
    private Factory (){}
//--------------------------------- NasFileManager --------------------------------------------------------------*/

    public static List<FileInfo> listFolderContents (@NotNull String folderName)
    {
        return NasFileManager.listFolderContents(folderName);
    }

    public static List<FileInfo> listFolderContents (@NotNull Path folder)
    {
        return NasFileManager.listFolderContents (folder);
    }

    public static boolean isStringOfRealPath (@NotNull String string, String ... strings)
    {
        return NasFileManager.isStringOfRealPath(string, strings);
    }

    public static String stringPath2StringAbsoluteParentPath (@NotNull String s)
    {
        return NasFileManager.stringPath2StringAbsoluteParentPath(s);
    }

    public static String formatFileTime (long time)
    {
        return NasFileManager.formatFileTime (time);
    }

    public static boolean checkUserFolder (@NotNull String userName)
    {
        return NasFileManager.checkUserFolder(userName);
    }

    public static Path constructAbsoluteUserPath (@NotNull String userName)
    {
        return NasFileManager.constructAbsoluteUserPath (userName);
    }

    public static Path absolutePathToUserSpace (@NotNull String userName, @NotNull Path path, boolean mustBeFolder)
    {
        return NasFileManager.absolutePathToUserSpace(userName, path, mustBeFolder);
    }

    public static FileInfo createSubfolder4User (@NotNull Path currentDir, @NotNull String userName, @NotNull String strNewDirName)
    {
        return NasFileManager.createSubfolder4User (currentDir, userName, strNewDirName);
    }

    public static Path relativizeByUserName (@NotNull String userName, @NotNull Path path)
    {
        return NasFileManager.relativizeByUserName (userName, path);
    }

    public static FileInfo rename (@NotNull Path pathParentAbsolute, @NotNull String oldName, @NotNull String newName)
    {
        return NasFileManager.rename(pathParentAbsolute, oldName, newName);
    }

    public static String safeRelativeLevelUpStringFrom (@NotNull String userName, @NotNull String fromFolder)
    {
        return NasFileManager.safeRelativeLevelUpStringFrom (userName, fromFolder);
    }

    public static FileInfo getSafeFileInfo (@NotNull String userName, @NotNull String folder, @NotNull String file)
    {
        return NasFileManager.getSafeFileInfo (userName, folder, file);
    }

    public static int countDirectoryEntries (@NotNull Path folder)
    {
        return NasFileManager.countDirectoryEntries (folder);
    }

    public static boolean deleteFileOrDirectory (@NotNull Path path)
    {
        return NasFileManager.deleteFileOrDirectory (path);
    }

    public static Path createSubfolder (Path parent, String strChild)
    {
        return NasFileManager.createSubfolder (parent, strChild);
    }

    public static boolean createCloudFolder (@NotNull String strCloudFolder)
    {
        return NasFileManager.createCloudFolder (strCloudFolder);
    }

    public static boolean checkUserNameValid (@NotNull String userName)
    {
        return NasFileManager.isUserNameValid(userName);
    }

    public static BasicFileAttributes readBasicFileAttributes2 (@NotNull Path path)
    {
        return NasFileManager.readBasicFileAttributes2 (path);
    }

//---------------------------------- FileInfo -------------------------------------------------------------------*/

    public static FileInfo ficopy (FileInfo fi)
    {
        return FileInfo.copy(fi);
    }


//---------------------------------- NasMsg ---------------------------------------------------------------------*/

    public static NasMsg nmcopy (NasMsg nm)
    {
        return NasMsg.nmcopy(nm);
    }

//---------------------------------------------------------------------------------------------------------------*/

//Возвращает true, если ни одна из строк lines не пустая и не равна null.
    public static boolean sayNoToEmptyStrings (String ... lines)    //+
    {
        boolean result = lines != null;
        if (result)
        for (String s : lines)
            if (s == null || s.trim().isEmpty())
            {
                result = false;     break;
            }
        return result;
    }

//К сожалению, метод print (см. sun.misc) зарезервирован для служебных целей. Исправляю этот недостаток.
    public static void print (String s)   {   System.out.print(s);   }        //+

//Предпочитаю, чтобы мои строки по умолчанию выводились с новой строки, а не являлись продолжением неизвестно какого текста.
    public static void lnprint (String s)   {   System.out.print("\n"+s);   }       //+

//Унифицируем создание коллекции для списка файлов.
    public static List<FileInfo> newInfolist ()    {   return new ArrayList<>();   }        //+

}
//---------------------------------------------------------------------------------------------------------------*/
