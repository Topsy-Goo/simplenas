package ru.gb.simplenas.server.services;
import ru.gb.simplenas.server.services.impl.NasServerManipulator;

import java.nio.file.Path;

public interface Server
{

    boolean clientsListAdd (NasServerManipulator manipulator, String userName);

    void clientsListRemove (NasServerManipulator manipulator, String userName);

    //Path getCloudPath ();

}
