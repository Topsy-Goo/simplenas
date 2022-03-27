package ru.gb.simplenas.common.services.impl;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import ru.gb.simplenas.common.CommonData;
import ru.gb.simplenas.common.structs.FileInfo;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;


public final class NasFileManager {

    private static final Logger LOGGER = LogManager.getLogger(NasFileManager.class.getName());


    // !!! Метод НЕ проверяет, находится ли strChild в STRPATH_CLOUD или в адресном пространстве юзера.
    public static Path createSubfolder (@NotNull Path pParent, @NotNull String strChild) {

        Path result = null;
        if (pParent != null && sayNoToEmptyStrings(strChild)) {
            Path pChild = pParent.toAbsolutePath().resolve(strChild).normalize();
            result = createFolder(pChild);
        }
        return result;
    }

    public static Path createFolder (@NotNull Path pFolder) {   //fm, sfm

        Path result = null;
        if (pFolder != null) {
            if (Files.exists(pFolder)) {
                if (Files.isDirectory(pFolder))
                    result = pFolder;
                else
                LOGGER.error("createFolder(): !!!!! Не удалось создать папку, — существует файл: <"
                             + pFolder.toString() + ">");
            }
            else
            try {
                result = Files.createDirectories(pFolder);
                LOGGER.trace("createFolder(): создана папка: " + pFolder.toString());
            }
            catch (IOException e) {e.printStackTrace();}
        }
        return result;
    }

    public static boolean createFile (@NotNull String strFile) {
        if (!sayNoToEmptyStrings(strFile))
            return false;
        return createFile(Paths.get(strFile));
    }

    public static boolean createFile (@NotNull Path pFile) {
        boolean result = false;
        if (pFile != null) {
            if (!Files.exists(pFile)) {
                try {
                    Files.createFile(pFile);
                    result = true;
                }
                catch (IOException e) {e.printStackTrace();}
            }
            else { result = true; }
        }
        return result;
    }

/** @return FileInfo, если переименование удалось, и NULL в противном случае. */
    public static FileInfo rename (@NotNull Path pParent, @NotNull String oldName,
                                   @NotNull String newName)
    {
        FileInfo result = null;
        if (pParent != null && sayNoToEmptyStrings(oldName, newName)) {
            Path pParentAbsolute = pParent.toAbsolutePath().normalize();
            Path pOlderSource = resolveFileNameAgainstPath(pParentAbsolute, oldName);
            Path pNewerTarget = resolveFileNameAgainstPath(pParentAbsolute, newName);

            if (pOlderSource != null && pNewerTarget != null && Files.exists(pOlderSource) && Files.notExists(pNewerTarget)) {
                try {
                    Files.move (pOlderSource, pNewerTarget);
                    result = new FileInfo (pNewerTarget);
                    LOGGER.trace ("rename(): переименование из\n\t\t<" + pOlderSource.toString() + ">\n\t\tв <" + pNewerTarget.getFileName().toString() + ">");
                }
                catch (IOException e) { e.printStackTrace(); }
            }
        }
        return result;
    }

    //добавляем к пути pParent ещё одно звено strPath, убедившись, что добавляется только одно звено.
    public static Path resolveFileNameAgainstPath (@NotNull Path pParent, @NotNull String strPath)
    {
        if (strPath != null && pParent != null) {
            Path pFileName = Paths.get(strPath);

            if (pFileName.getNameCount() == 1)
                return pParent.resolve(pFileName);
        }
        return null;
    }

    //укорачиваем относительный путь на одно звено, если есть такая возможность. Путь не обязан существовать.
    public static String getParentFromRelative (@NotNull String strRoot, @NotNull String strFromFolder)
    {
        String result = null;
        if (sayNoToEmptyStrings(strRoot, strFromFolder)) {
            Path pFrom = Paths.get(strFromFolder).normalize();

            if (pFrom.getNameCount() <= 2)
                result = strRoot;
            else
                result = pFrom.getParent().toString();
        }
        return result;
    }

/** Определяем количество элементов ФС в указанной папке. */
    public static long countDirectoryEntries (Path pFolder) {
        long result = -1;
        if (pFolder != null && Files.isDirectory(pFolder) && Files.exists(pFolder)) {
            //int[] counter = {0}; //< нужна effectively final-переменная
            try {
                //Files.newDirectoryStream(pFolder).forEach((p)->counter[0]++); //    < это тоже работает
                //Files.list(pFolder).forEach((p)->counter[0]++);    < это тоже работает
                //result = counter[0];
                result = Files.list(pFolder).count();
            }
            catch (IOException e) {e.printStackTrace();}
        }
        return result;
    }

    public static boolean deleteFileOrDirectory (@NotNull Path path) {   //ServerManipulator, Controller

        boolean result = false;
        if (path != null) {
            try {
                if (Files.isDirectory(path)) {
                    FileUtils.deleteDirectory(path.toFile());
                    LOGGER.info("удалён каталог : <" + path.toString() + ">");
                    result = true;
                }
                else if (Files.isRegularFile(path)) {
                    Files.delete(path);
                    LOGGER.info("удалён файл : <" + path.toString() + ">");
                    result = true;
                }
            }
            catch (IOException e) {e.printStackTrace();}
        }
        return result;
    }

    // Заполняем структуру FileInfo fi данными, соответствующими файлу, на который указывает Path path.
    public static BasicFileAttributes readBasicFileAttributes2 (@NotNull Path path) {
        if (path != null) {
            try {
                BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                //Returns:  a file attribute view of the specified type,
                //          or null if the attribute view type is not available
                if (view != null)
                    return view.readAttributes();
            }
            catch (IOException e) {e.printStackTrace();}
        }
        return null;
    }

/** Составляем список объектов ФС, расположенных в указанной папке. Список содержит только
объекты первого уровня вложенности. */
    public static @NotNull List<FileInfo> listFolderContents (@NotNull String strFolderName)
    {
        return (sayNoToEmptyStrings(strFolderName))
                    ? listFolderContents(Paths.get(strFolderName))
                    : newFileInfoList();
    }

    public static @NotNull List<FileInfo> newFileInfoList () { return new ArrayList<>(); }

/** Составляем список объектов ФС, расположенных в указанной папке. Список содержит только
объекты первого уровня вложенности. */
    public static @NotNull List<FileInfo> listFolderContents (@NotNull Path pFolder)
    {
        List<FileInfo> infolist = newFileInfoList();
        if (pFolder != null) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pFolder);) {
                for (Path p : directoryStream)
                    infolist.add(new FileInfo(p));
            }
            catch (IOException | DirectoryIteratorException e) {e.printStackTrace();}
        }
        return infolist;
    }

/** разрешаем юзеру использовать только буквы и цыфры при указании логина. (Предполагаем,
что буквы и цифры разрешены в именах папок на всех платформах.)  */
    public static boolean isNameValid (@NotNull String userName) {
        boolean boolOk = false;
        if (sayNoToEmptyStrings(userName)) {
            int len = userName.length();

            if (len <= MAX_USERNAME_LENGTH) {
                if (len == userName.trim().length()) {
                    for (Character ch : userName.toCharArray()) {
                        boolOk = Character.isAlphabetic(ch) || Character.isDigit(ch);

                        if (boolOk) {
                            //Эта проверка должна производиться ПОСЛЕ проверки символов, чтобы не ловить исключения.
                            Path path = Paths.get(userName);
                            boolOk = path.getNameCount() == 1;
                        }
                        if (!boolOk) break;
                    }
                }
            }
        }
        return boolOk;
    }

    public static String relativizeByFolderName (@NotNull String FolderName, @NotNull String strPath)
    {
        String result = STR_EMPTY;
        Path path = Paths.get(strPath);
        Path subpath = null;
        int firstFoundIndex = -1;
        int counter = 0;
        int lenth = path.getNameCount();

        for (Path p : path) {
            if (p.toString().equals(FolderName)) {
                firstFoundIndex = counter;
                break;
            }
            counter++;
        }
        if (firstFoundIndex >= 0) {
            if (lenth > 1) {  //sun.nio.fs.WindowsPath.subpath() не любит, когда beginIndex == endIndex
                subpath = path.subpath(firstFoundIndex + 1, lenth);
                result = subpath.toString();
            }
        }
        return result;
    }

/** Возвращает строку пути к родительской папке. Родительская папка должна существовать.  */
    public static String stringPath2StringAbsoluteParentPath (String strPath)
    {
        String parent = "";
        if (strPath != null) {
            Path path = Paths.get(strPath).toAbsolutePath().normalize().getParent();
            if (path != null && Files.exists(path))
                parent = path.toString();
        }
        return parent;
    }

/** Определяем, указывает строка параметра на действительно существующий объект ФС. */
    public static boolean isStringOfRealPath (@NotNull String string) {
        boolean boolYes = false;
        if (string != null) {
            try {
                boolYes = Files.exists (Paths.get(string));
            }
            catch (InvalidPathException e) { e.printStackTrace(); }
        }
        return boolYes;
    }

    public static boolean isFileExists (@NotNull Path path) {
        return Files.exists (path);
    }

/*    public static boolean isFolderExists (@NotNull Path path) {

    }*/

/** Возвращаем Path-ссылку на папку, на которую указывает строка параметра.
@param strFolder строка относительного или абсолютного пути к папке.
@return Path, если строка указывает на существующую папку, или NULL в противном случае. */
    public static Path stringToExistingFolder (String strFolder) {
        Path path = Paths.get (strFolder).toAbsolutePath().normalize();
        return (Files.exists (path) && Files.isDirectory (path))
                ? path
                : null;
    }

/** Определяем, доступен ли для чтения объект файловой системы — файл или папка, на который
указывает параметр. Некоторые файлы и папки, доступ к которым закрыт ОСью, вызывают неправильное
поведение приложения, если не проводить такую проверку. */
    public static boolean isReadable (@NotNull Path path) {
        return Files.isReadable (path);
    }

    public static long fileSize (@NotNull Path path) throws IOException {
        return Files.size (path);
    }

//Преобразуем размер файла в строку, удобную для отображения в свойтсвах файла в GUI.
//(Можно также использовать метод FileUtils.byteCountToDisplaySize(), но он не так хорош.)
    public static String fileSizeToString (long fsize)
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

    public static String formatFileTime (long time) {
        FileTime          ft  = FileTime.from (time, FILETIME_UNITS);
        LocalDateTime     ldt = LocalDateTime.ofInstant (ft.toInstant(), ZONE_ID);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern (FILETIME_FORMAT_PATTERN, RU_LOCALE);
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
