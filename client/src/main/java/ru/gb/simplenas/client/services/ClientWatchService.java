package ru.gb.simplenas.client.services;

public interface ClientWatchService {

    void startWatchingOnFolder (String strFolder);

    void close ();
}
