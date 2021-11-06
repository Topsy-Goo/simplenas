package ru.gb.simplenas.common;

import org.jetbrains.annotations.NotNull;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Factory {

    private Factory () {}
//---------------------------------- FileInfo -------------------------------------------------------------------*/

    public static FileInfo ficopy (FileInfo fi) {
        return FileInfo.copy(fi);
    }
//---------------------------------- NasMsg ---------------------------------------------------------------------*/

    public static NasMsg nmcopy (NasMsg nm) {
        return NasMsg.nmcopy(nm);
    }
//---------------------------------------------------------------------------------------------------------------*/

/** Возвращает true, если ни одна из строк lines не пустая и не равна null. */
    public static boolean sayNoToEmptyStrings (String... lines) {

        if (lines != null) {
            for (String s : lines) {
                if (s == null || s.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

/** К сожалению, метод print (см. sun.misc) зарезервирован для служебных целей. Исправляю этот недостаток.  */
    public static void print (String s) { System.out.print(s); }

    public static void errprint (String s) { System.err.print(s); }

/** Предпочитаю, чтобы мои строки по умолчанию выводились с новой строки, а не являлись продолжением неизвестно какого текста.  */
    public static void lnprint (String s) { System.out.print("\n" + s); }

    public static void printf (String strFormat, Object... args) {
        System.out.format(strFormat, args);
    }

    public static void errprintf (String strFormat, Object... args) {
        System.err.format(strFormat, args);
    }

/** Унифицируем создание коллекции для списка файлов.   */
    public static List<FileInfo> newInfolist () { return new ArrayList<>(); }

    public static String sformat (String format, Object... objects) {
        return String.format(format, objects);
    }

//--------------------------------- NasFileManager --------------------------------------------------------------*/

    public static BasicFileAttributes readBasicFileAttributes2 (@NotNull Path path) {
        return NasFileManager.readBasicFileAttributes2(path);
    }

    public static List<FileInfo> listFolderContents (@NotNull String folderName) {
        return NasFileManager.listFolderContents(folderName);
    }

    public static List<FileInfo> listFolderContents (@NotNull Path folder) {
        return NasFileManager.listFolderContents(folder);
    }

    public static boolean createFile (@NotNull Path pFile) {
        return NasFileManager.createFile(pFile);
    }

    public static boolean createFile (@NotNull String strFile) {
        return NasFileManager.createFile(strFile);
    }

    //public static String relativizeByFolderName (@NotNull String folderName, @NotNull String strPath)
    //{
    //    return NasFileManager.relativizeByFolderName(folderName, strPath);
    //}

    public static boolean isUserNameValid (String userName) {
        return NasFileManager.isNameValid(userName);
    }
}
