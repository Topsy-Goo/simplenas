package ru.gb.simplenas.server.services;

import ru.gb.simplenas.common.structs.FileInfo;

import java.nio.file.Path;

public interface ServerFileManager {

    boolean checkUserFolder (String userName);

    String safeRelativeParentStringFrom (String userName, String fromFolder);

    Path getSafeAbsolutePathBy (Path path, String userName);

    Path relativizeByUserName (String userName, Path path);

    Path constructAbsoluteUserRoot (String userName);

    FileInfo getSafeFileInfo (String userName, String folder, String file);

    FileInfo createSubfolder4User (Path pParent, String userName, String strChild);

    Path absolutePathToUserSpace (String userName, Path path, boolean mustBeFolder);

    FileInfo safeRename (Path pParent, String oldName, String newName, String userName);

    long safeCountDirectoryEntries (Path pFolder, String userName);

    boolean safeDeleteFileOrDirectory (Path path, String userName);

    Path getCloud();
}
