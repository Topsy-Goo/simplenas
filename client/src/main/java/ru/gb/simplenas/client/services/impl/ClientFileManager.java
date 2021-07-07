package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import ru.gb.simplenas.common.CommonData;
import ru.gb.simplenas.common.services.impl.NasFileManager;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;

/*  Отличительной чертой этих методов и причиной их вынесения в отдельный класс является то, что они не
    содержат проверку на выход из дискового пространства пользователя (ДПП). Они предназначены для работы
    на локальном ПК пользователя.
*/
public class ClientFileManager extends NasFileManager
{

    private ClientFileManager () {}

    public static boolean isStringOfRealPath (@NotNull String string, String ... strings)   //Controller
    {
        boolean boolYes = false;
        if (string != null)
        {
            try
            {   boolYes = Files.exists(Paths.get(string, strings));
            }
            catch (InvalidPathException e) { e.printStackTrace(); }
        }
        return boolYes;
    }

//Возвращает строку пути к родительской папке. Родительская папка должна существовать.
    public static @NotNull String stringPath2StringAbsoluteParentPath (@NotNull String strPath)     //Controller
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
    public static String fileSizeToString (long fsize)      //-
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

    public static String formatFileTime (long time)     //TableFileInfo
    {
        FileTime ft = FileTime.from(time, CommonData.filetimeUnits);
        LocalDateTime ldt = LocalDateTime.ofInstant(ft.toInstant(), ZoneId.systemDefault());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(CommonData.FILETIME_FORMAT_PATTERN, CommonData.RU_LOCALE);
        return ldt.format(dtf);
    }

// !!! Метод НЕ проверяет, находится ли strChild в STRPATH_CLOUD или в адресном пространстве юзера.
    public static Path createSubfolder (@NotNull Path pParent, @NotNull String strChild)   //Controller
    {
        Path result = null;
        if (pParent != null && sayNoToEmptyStrings (strChild))
        {
            Path pChild = pParent.toAbsolutePath().resolve(strChild).normalize();
            result = createFolder (pChild);
        }
        return result;
    }

}
