package ru.gb.simplenas.server.services;
import ru.gb.simplenas.server.services.impl.RemoteManipulator;

public interface Server
{

    boolean clientsListAdd (RemoteManipulator manipulator, String userName);

    void clientsListRemove (RemoteManipulator manipulator, String userName);

    //Path getCloudPath ();

}
