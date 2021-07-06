package ru.gb.simplenas.server.services;
import ru.gb.simplenas.server.services.impl.NasServerManipulator;

//
public interface Server
{
    //void clientsListAdd (Channel channel, String userName);
    boolean clientsListAdd (NasServerManipulator manipulator, String userName);

    void clientsListRemove (NasServerManipulator manipulator, String userName);

    //Channel getUserData (String userName);

//1
}// interface Server
