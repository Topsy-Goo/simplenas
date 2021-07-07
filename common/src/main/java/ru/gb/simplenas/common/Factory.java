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

//---------------------------------------------------------------------------------------------------------------*/

    public static BasicFileAttributes readBasicFileAttributes2 (@NotNull Path path)
    {
        return NasFileManager.readBasicFileAttributes2(path);
    }

    public static List<FileInfo> listFolderContents (@NotNull String folderName)
    {
        return NasFileManager.listFolderContents(folderName);
    }

    public static List<FileInfo> listFolderContents (@NotNull Path folder)
    {
        return NasFileManager.listFolderContents(folder);
    }

}
//---------------------------------------------------------------------------------------------------------------*/
