package ru.gb.simplenas.client.services;

import java.io.Closeable;

public interface LocalPropertyManager
{

    void initialize();

    int getRemotePort();
    String getHostString();
    String getLastLocalPathString();
    String getLastRemotePathString();

    void setLastLocalPathString (String strLocalPath);
    void setLastRemotePathString (String strRemote);

    void close();
}
