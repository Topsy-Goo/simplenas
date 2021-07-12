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
import static ru.gb.simplenas.common.CommonData.STR_EMPTY;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;


public class NasFileManager {
    private static final Logger LOGGER = LogManager.getLogger(NasFileManager.class.getName());

    protected NasFileManager () {}

    protected static Path createFolder (@NotNull Path pFolder) {
        Path result = null;
        if (pFolder != null) {
            if (Files.exists(pFolder)) {
                if (Files.isDirectory(pFolder)) {
                    result = pFolder;
                }
                else { LOGGER.error("createFolder(): !!!!! Не удалось создать папку, — существует файл: <" + pFolder.toString() + ">"); }
            }
            else {
                try {
                    result = Files.createDirectories(pFolder);
                    LOGGER.trace("createFolder(): создана папка: " + pFolder.toString());
                }
                catch (IOException e) {e.printStackTrace();}
            }
        }
        return result;
    }

    public static boolean createFile (@NotNull String strFile) {
        if (!sayNoToEmptyStrings(strFile)) { return false; }
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

    public static FileInfo rename (@NotNull Path pParent, @NotNull String oldName, @NotNull String newName) {
        FileInfo result = null;
        if (pParent != null && sayNoToEmptyStrings(oldName, newName)) {
            Path pParentAbsolute = pParent.toAbsolutePath().normalize();
            Path pOlderSource = resolveFileNameAgainstPath(pParentAbsolute, oldName);
            Path pNewerTarget = resolveFileNameAgainstPath(pParentAbsolute, newName);

            if (pOlderSource != null && pNewerTarget != null && Files.exists(pOlderSource) && Files.notExists(pNewerTarget)) {
                try {
                    Files.move(pOlderSource, pNewerTarget);
                    result = new FileInfo(pNewerTarget);
                    LOGGER.trace("rename(): переименование из\n\t\t<" + pOlderSource.toString() + ">\n\t\tв <" + pNewerTarget.getFileName().toString() + ">");
                }
                catch (IOException e) {e.printStackTrace();}
            }
        }
        return result;
    }

    protected static Path resolveFileNameAgainstPath (@NotNull Path pParent, @NotNull String strPath) {
        if (strPath != null && pParent != null) {
            Path pFileName = Paths.get(strPath);
            if (pFileName.getNameCount() == 1) {
                return pParent.resolve(pFileName);
            }
        }
        return null;
    }

    public static String getParentFromRelative (@NotNull String strRoot, @NotNull String strFromFolder) {
        String result = null;
        if (sayNoToEmptyStrings(strRoot, strFromFolder)) {
            Path pFrom = Paths.get(strFromFolder).normalize();
            if (pFrom.getNameCount() <= 2) {
                result = strRoot;
            }
            else {
                result = pFrom.getParent().toString();
            }
        }
        return result;
    }

    public static int countDirectoryEntries (@NotNull Path pFolder) {
        int result = -1;
        if (pFolder != null && Files.isDirectory(pFolder) && Files.exists(pFolder)) {
            int[] counter = {0};
            try {
                Files.newDirectoryStream(pFolder).forEach((p)->counter[0]++);
                result = counter[0];
            }
            catch (IOException e) {e.printStackTrace();}
        }
        return result;
    }

    public static boolean deleteFileOrDirectory (@NotNull Path path) {
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

    public static BasicFileAttributes readBasicFileAttributes2 (@NotNull Path path) {
        if (path != null) {
            try {
                BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                if (view != null) {
                    return view.readAttributes();
                }
            }
            catch (IOException e) {e.printStackTrace();}
        }
        return null;
    }

    public static @NotNull List<FileInfo> listFolderContents (@NotNull String strFolderName) {
        return (sayNoToEmptyStrings(strFolderName)) ? listFolderContents(Paths.get(strFolderName)) : newFileInfoList();
    }

    protected static @NotNull List<FileInfo> newFileInfoList () { return new ArrayList<>(); }

    public static @NotNull List<FileInfo> listFolderContents (@NotNull Path pFolder) {
        List<FileInfo> infolist = newFileInfoList();
        if (pFolder != null) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pFolder);) {
                for (Path p : directoryStream) {
                    infolist.add(new FileInfo(p));
                }
            }
            catch (IOException | DirectoryIteratorException e) {e.printStackTrace();}
        }
        return infolist;
    }

    public static boolean isNameValid (@NotNull String userName) {
        boolean boolOk = false;
        if (sayNoToEmptyStrings(userName) && userName.length() <= MAX_USERNAME_LENGTH) {
            for (Character ch : userName.toCharArray()) {
                boolOk = Character.isAlphabetic(ch) || Character.isDigit(ch);
                if (!boolOk) { break; }
            }
        }
        return boolOk;
    }

    public static String relativizeByFolderName (@NotNull String FolderName, @NotNull String strPath) {
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
            if (lenth > 1) {
                subpath = path.subpath(firstFoundIndex + 1, lenth);
                result = subpath.toString();
            }
        }
        return result;
    }

}
