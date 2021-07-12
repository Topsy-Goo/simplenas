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

public class LocalFileManager extends NasFileManager {

    private LocalFileManager () {}

    public static boolean isStringOfRealPath (@NotNull String string, String... strings) {
        boolean boolYes = false;
        if (string != null) {
            try {
                boolYes = Files.exists(Paths.get(string, strings));
            }
            catch (InvalidPathException e) { e.printStackTrace(); }
        }
        return boolYes;
    }

    public static @NotNull String stringPath2StringAbsoluteParentPath (@NotNull String strPath) {
        String parent = "";
        if (strPath != null) {
            Path path = Paths.get(strPath).toAbsolutePath().normalize().getParent();
            if (path != null && Files.exists(path)) {
                parent = path.toString();
            }
        }
        return parent;
    }

    public static String fileSizeToString (long fsize) {
        long Kilo = CommonData.FILESIZE_KILOBYTE;
        long Mega = Kilo * CommonData.FILESIZE_KILOBYTE;
        long Giga = Mega * CommonData.FILESIZE_KILOBYTE;
        long Tera = Giga * CommonData.FILESIZE_KILOBYTE;
        long r = fsize;
        String units = " байтов";

        if (fsize >= Kilo) {
            if (fsize < Giga) {
                r = fsize / Kilo;
                units = " Кб";
            }
            else if (fsize < Tera) {
                r = fsize / Mega;
                units = " Мб";
            }
            else {
                r = fsize / Tera;
                units = " Гб";
            }
        }
        return new DecimalFormat("###,###,###,###,###").format(r) + units;
    }

    public static String formatFileTime (long time) {
        FileTime ft = FileTime.from(time, CommonData.filetimeUnits);
        LocalDateTime ldt = LocalDateTime.ofInstant(ft.toInstant(), ZoneId.systemDefault());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(CommonData.FILETIME_FORMAT_PATTERN, CommonData.RU_LOCALE);
        return ldt.format(dtf);
    }

    public static Path createSubfolder (@NotNull Path pParent, @NotNull String strChild) {
        Path result = null;
        if (pParent != null && sayNoToEmptyStrings(strChild)) {
            Path pChild = pParent.toAbsolutePath().resolve(strChild).normalize();
            result = createFolder(pChild);
        }
        return result;
    }

}
