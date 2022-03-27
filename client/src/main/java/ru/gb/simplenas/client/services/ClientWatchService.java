package ru.gb.simplenas.client.services;

import ru.gb.simplenas.common.NasCallback;

public interface ClientWatchService {

    boolean setCallBack (NasCallback cb);

    void startWatchingOnFolder (String strFolder);

    void suspendWatching ();

    void resumeWatching (String strFolder);

    void close ();
}
