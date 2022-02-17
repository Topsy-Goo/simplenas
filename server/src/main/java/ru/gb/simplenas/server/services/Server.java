package ru.gb.simplenas.server.services;
import ru.gb.simplenas.server.services.impl.RemoteManipulator;

public interface Server extends Runnable {

    boolean clientsListAdd (RemoteManipulator manipulator, String userName);

    void clientRemove (RemoteManipulator manipulator, String userName);

    boolean validateOnLogin (String login, String password);
}
