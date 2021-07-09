package ru.gb.simplenas.server.services;

import java.nio.file.Path;
import java.util.List;

public interface ProperyManager
{
    void initialize();

    int getPublicPort();

    Path getCloudPath();

    List<String> getWelcomeFolders();

    List<String> getWelcomeFiles();

}
