package ru.gb.simplenas.client.services.impl;

import ru.gb.simplenas.common.CommonData;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.common.structs.FileInfo;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ru.gb.simplenas.client.CFactory.NO_SIZE_VALUE;
import static ru.gb.simplenas.common.CommonData.*;

/**  Отличительной чертой этих методов и причиной их вынесения в отдельный класс является то, что они не
 содержат проверку на выход из дискового пространства пользователя (ДПП). Они предназначены только для
 работы на локальном ПК пользователя.
*/
public class LocalFileManager extends NasFileManager {

    private LocalFileManager () {}

    //Преобразуем размер файла в строку, удобную для отображения в свойтсвах файла в GUI.
//(Можно также использовать метод FileUtils.byteCountToDisplaySize(), но он не так хорош.)
    public static String fileSizeToString (long fsize)      //-
    {
        long   Kilo  = CommonData.FILESIZE_KILOBYTE;
        long   Mega  = Kilo * CommonData.FILESIZE_KILOBYTE;
        long   Giga  = Mega * CommonData.FILESIZE_KILOBYTE;
        long   Tera  = Giga * CommonData.FILESIZE_KILOBYTE;
        long   r     = fsize;
        String units = " байтов";

        if (fsize >= Kilo) {
            if (fsize < Giga) {
                r = fsize / Kilo;   units = " Кб";
            }
            else if (fsize < Tera) {
                r = fsize / Mega;   units = " Мб";
            }
            else {
                r = fsize / Tera;   units = " Гб";
            }
        }
        return new DecimalFormat("###,###,###,###,###").format(r) + units;
    }

    public static String formatFileTime (long time) {    //TableFileInfo

        FileTime          ft  = FileTime.from (time, CommonData.filetimeUnits);
        LocalDateTime     ldt = LocalDateTime.ofInstant (ft.toInstant(), ZoneId.systemDefault());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern (CommonData.FILETIME_FORMAT_PATTERN, CommonData.RU_LOCALE);
        return ldt.format(dtf);
    }

    public static List<FileInfo> getRootsAsFileinfoList () {

        List<Path>     proots = new ArrayList<>();
        List<FileInfo> filist = new ArrayList<>();

        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            proots.add(root);
            filist.add(new FileInfo(root.toString(), FOLDER, EXISTS, NOT_SYMBOLIC, NO_SIZE_VALUE, 0L, 0L));
            // String name, boolean dir, boolean exists, boolean symbolic, long size, long created, long modified
        }
        return filist;
    }
}
