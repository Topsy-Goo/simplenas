package ru.gb.simplenas.client.services;

public interface ClientPropertyManager {

    void initialize ();

    int getUserFontSize (String userName);

    String getUserLocalPath (String userName);

    String getUserRemotePath (String userName);

    //void setUserFontSize (String userName);
    void setUserLastLocalPath (String userName, String strPath);

    void setUserLastRemotePath (String userName, String strPath);

    void setUserFontSize (String userName, int size);

    int getRemotePort ();

    String getHostString ();

    String getDefaultLastLocalPathString ();

    int getDefaultFontSize ();

    void setLastLocalPathString (String strLocalPath);

    void close ();
}
