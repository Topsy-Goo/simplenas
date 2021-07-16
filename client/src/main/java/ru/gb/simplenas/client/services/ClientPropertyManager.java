package ru.gb.simplenas.client.services;

public interface ClientPropertyManager
{

    void initialize();

    int getRemotePort();
    String getHostString();
    String getLastLocalPathString();
    String getLastRemotePathString();
    int getFontSize();

    void setLastLocalPathString (String strLocalPath);
    void setLastRemotePathString (String strRemote);

    void close();
}
