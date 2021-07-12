package ru.gb.simplenas.client.services;

import java.io.Closeable;

public interface ClientWatchService
{
    void startWatchingOnFolder (String strFolder);

    void close();

    //void stopWatching ();
}
