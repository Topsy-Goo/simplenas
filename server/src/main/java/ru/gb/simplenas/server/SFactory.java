package ru.gb.simplenas.server;

import ru.gb.simplenas.server.services.Server;
import ru.gb.simplenas.server.services.impl.NasServer;
import ru.gb.simplenas.server.services.impl.NasServerManipulator;
import ru.gb.simplenas.server.utils.impl.NasServerProperyManager;
import ru.gb.simplenas.server.utils.ServerProperyManager;

import java.util.List;

//
public class SFactory
{
    public static final String ERROR_INVALID_FILDER_SPECIFIED = "Указано некорректное имя папки.";

    private SFactory (){}

//-------------------------------------- NasServerManipulator ---------------------------------------------------*/

/*  если название метода просто Server() плохо смтрится в main(), то название startServer()
    плохо смотрится в остальном коде, поэтому пусть будут два одинаковых метода с разными
    названиями. */
    public static Server startSеrver ()  {   return NasServer.getInstance();   }
    public static Server server()  {   return NasServer.getInstance();   }

    public static boolean clientsListAdd (NasServerManipulator manipulator, String userName)
    {
        return server().clientsListAdd(manipulator, userName);
    }

    public static void clientsListRemove (NasServerManipulator manipulator, String userName)
    {
        server().clientsListRemove(manipulator, userName);
    }

//-------------------------------------- NasServerPropertyManager -----------------------------------------------*/

    public static ServerProperyManager nasProperyManager()  {   return NasServerProperyManager.getInstance();   }

    public static int getPortProperty()
    {
        return nasProperyManager().getPortProperty();
    }

    public static List<String> getWelcomeFileList()
    {
        return nasProperyManager().getWelcomeFileList();
    }

    public static List<String> getWelcomeDirsList()
    {
        return nasProperyManager().getWelcomeDirsList();
    }


}
//---------------------------------------------------------------------------------------------------------------*/