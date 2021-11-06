package ru.gb.simplenas.server.services;

import java.util.List;

public interface ServerPropertyManager {

    void initialize();

    int getPublicPort();

    String getCloudName();

    List<String> getWelcomeFolders();

    List<String> getWelcomeFiles();
}
