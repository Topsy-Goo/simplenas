package ru.gb.simplenas.server.utils;

import java.util.List;

public interface ServerProperyManager
{
    int getPortProperty();

    List<String> getWelcomeFileList();

    List<String> getWelcomeDirsList();

    String getCloudDirectory();

}
