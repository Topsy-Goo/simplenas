package ru.gb.simplenas.client.services;

import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

public interface NetClient extends Runnable {
    boolean connect ();

    NasMsg login (String userName);

    void disconnect ();

    NasMsg list (String folder, String... subfolders);

    NasMsg goTo (String folder, String... subfolder);

    NasMsg levelUp (String fromFolder);

    NasMsg create (String childFolder);

    NasMsg rename (FileInfo from, String to);

    NasMsg transferFile (String strLocal, String strRemote, FileInfo fileInfo, OperationCodes opcode);

    FileInfo fileInfo (String folder, String fileName);

    int countFolderEntries (String strParent, FileInfo fi);

    NasMsg delete (String strParent, FileInfo fi);

}
