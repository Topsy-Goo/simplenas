package ru.gb.simplenas.client.services;

import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;

public interface NetClient extends Runnable
{
    boolean connect ();

    NasMsg login (String userName);

    void disconnect ();

    NasMsg list (String folder, String... subfolders);

    NasMsg goTo (String folder, String... subfolder);

    NasMsg create (String childFolder);

    NasMsg rename (FileInfo from, String to);

    NasMsg download (String strLocalFolder, String strServerFolder, FileInfo fileInfo);

    NasMsg upload (String strLocalFolder, String strServerFolder, FileInfo fileInfo);

    FileInfo fileInfo (String folder, String fileName);

    int countFolderEntries (String strParent, FileInfo fi);

    NasMsg delete (String strParent, FileInfo fi);

}
