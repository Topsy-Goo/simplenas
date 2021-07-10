package ru.gb.simplenas.server.services;

import java.nio.file.Path;
import java.util.List;

public interface PropertyManager
{
    void initialize();

    int getPublicPort();

    String getCloudName();

    List<String> getWelcomeFolders();

    List<String> getWelcomeFiles();

}
