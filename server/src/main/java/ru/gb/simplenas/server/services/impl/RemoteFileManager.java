package ru.gb.simplenas.server.services.impl;

import com.sun.istack.internal.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.server.services.ServerFileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;

public class RemoteFileManager extends NasFileManager implements ServerFileManager {
    private static final Logger LOGGER = LogManager.getLogger(RemoteFileManager.class.getName());
    private Path cloud;
    private List<String> welcomeFolders;
    private List<String> welcomeFiles;


    public RemoteFileManager (String strCloud) {
        if (!sayNoToEmptyStrings(strCloud)) { throw new IllegalArgumentException(); }

        this.cloud = Paths.get(strCloud).toAbsolutePath().normalize();
        LOGGER.debug("создан RemoteFileManager");
    }

    public RemoteFileManager (String strCloudName, List<String> welcomeFolders, List<String> welcomeFiles) {
        this(strCloudName);
        if (!checkCloudFolder()) { throw new IllegalArgumentException(); }

        if (welcomeFolders == null) { welcomeFolders = new ArrayList<>(); }
        this.welcomeFolders = welcomeFolders;
        if (welcomeFiles == null) { welcomeFiles = new ArrayList<>(); }
        this.welcomeFiles = welcomeFiles;
    }

    private boolean checkCloudFolder () {
        if (cloud == null) { return false; }

        boolean exists = Files.exists(cloud);
        if (!exists) {
            cloud = createFolder(cloud);
            if (cloud != null) { exists = Files.exists(cloud); }
        }
        return exists && Files.isDirectory(cloud);
    }

    @Override public FileInfo getSafeFileInfo (@NotNull String userName, @NotNull String folder, @NotNull String file) {
        FileInfo result = null;
        if (sayNoToEmptyStrings(userName, folder, file)) {
            Path path = getSafeAbsolutePathBy(Paths.get(folder, file), userName);
            if (path != null) {
                result = new FileInfo(path);
            }
        }
        return result;
    }

    @Override public FileInfo createSubfolder4User (@NotNull Path pParent, @NotNull String userName, @NotNull String strChild) {
        FileInfo result = null;
        if (pParent != null) {
            Path pChildAbsolute = getSafeAbsolutePathBy(pParent.resolve(strChild), userName);
            if (pChildAbsolute != null) {
                Path p = createFolder(pChildAbsolute);
                if (p != null) {
                    result = new FileInfo(p);
                }
            }
        }
        return result;
    }

    @Override public boolean checkUserFolder (@NotNull String userName) {
        boolean result = false;
        Path userroot = constructAbsoluteUserRoot(userName);

        if (userroot != null) {
            if (!Files.exists(userroot) && createFolder(userroot) != null) {
                try {
                    createNewUserFolders(userroot);
                    createNewUserFiles(userroot);
                }
                catch (IOException e) {e.printStackTrace();}
            }
            result = Files.exists(userroot);
        }
        return result;
    }

    private void createNewUserFolders (Path userroot) throws IOException {
        if (userroot != null && welcomeFolders != null) {
            for (String s : welcomeFolders) {
                Path dir = userroot.resolve(s);
                createFolder(dir);
            }
        }
    }

    private void createNewUserFiles (@NotNull Path user) throws IOException {
        if (user != null && welcomeFiles != null) {
            for (String s : welcomeFiles) {
                Path pSrcFile = cloud.resolve(s);
                Path pTargetFile = user.resolve(s);

                if (Files.exists(pSrcFile)) {
                    if (!Files.exists(pTargetFile)) { Files.copy(pSrcFile, pTargetFile); }
                }
                else { LOGGER.warn("createNewUserFiles(): !!!!! файл не найден : <" + pSrcFile.toString() + ">"); }
            }
        }
    }

    @Override public Path absolutePathToUserSpace (@NotNull String userName, @NotNull Path path, boolean mustBeFolder) {
        Path result = null;
        if (path != null) {
            Path tmp = getSafeAbsolutePathBy(path, userName);
            if (tmp != null && mustBeFolder == Files.isDirectory(tmp)) {
                result = tmp;
            }
        }
        return result;
    }

    @Override public FileInfo safeRename (@NotNull Path pParent, @NotNull String oldName, @NotNull String newName, @NotNull String userName) {
        FileInfo result = null;
        if ((pParent = getSafeAbsolutePathBy(pParent, userName)) != null) {
            result = rename(pParent, oldName, newName);
        }
        return result;
    }

    @Override public Path constructAbsoluteUserRoot (@NotNull String userName) {
        Path userroot = null;

        if (isNameValid(userName)) {
            Path ptmp = Paths.get(userName);
            if (ptmp.getNameCount() == 1) {
                userroot = cloud.resolve(ptmp);
            }
        }
        return userroot;
    }

    @Override public Path relativizeByUserName (@NotNull String userName, @NotNull Path path) {
        Path result = null;

        if (path != null) {
            Path userroot = constructAbsoluteUserRoot(userName);
            if (userroot != null) {
                if (!path.isAbsolute()) {
                    path = cloud.resolve(path);
                }
                path = path.normalize();

                if (path.startsWith(userroot)) {
                    result = cloud.relativize(path);
                }
            }
        }
        return result;
    }

    @Override public int safeCountDirectoryEntries (@NotNull Path pFolder, @NotNull String userNAme) {
        Path tmp = getSafeAbsolutePathBy(pFolder, userNAme);
        if (tmp != null) {
            return countDirectoryEntries(tmp);
        }
        return -1;
    }

    @Override public boolean safeDeleteFileOrDirectory (@NotNull Path path, @NotNull String userNAme) {
        Path tmp = getSafeAbsolutePathBy(path, userNAme);
        if (tmp != null) {
            return deleteFileOrDirectory(path);
        }
        return false;
    }

    @Override public Path getSafeAbsolutePathBy (@NotNull Path path, @NotNull String userName) {
        Path result = null, userroot = constructAbsoluteUserRoot(userName);

        if (path != null && userroot != null && cloud != null) {
            if (!path.isAbsolute()) {
                path = cloud.resolve(path);
            }
            path = path.normalize();
            if (path.startsWith(userroot)) {
                result = path;
            }
        }
        return result;
    }

    @Override public String safeRelativeParentStringFrom (@NotNull String userName, @NotNull String fromFolder) {
        String result = null;

        if (sayNoToEmptyStrings(userName, fromFolder)) {
            Path userroot = constructAbsoluteUserRoot(userName);
            Path path = cloud.resolve(fromFolder).normalize().getParent();

            if (path.startsWith(userroot)) {
                result = cloud.relativize(path).toString();
            }
        }
        return result;
    }

}
