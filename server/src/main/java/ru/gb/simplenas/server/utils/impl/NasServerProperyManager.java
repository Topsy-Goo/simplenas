package ru.gb.simplenas.server.utils.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.server.utils.ServerProperyManager;

import java.util.List;
import java.util.Properties;

public class NasServerProperyManager implements ServerProperyManager
{
    private static ServerProperyManager instance;
    private static final Properties properties = new Properties();
    private static final Logger LOGGER = LogManager.getLogger(NasServerProperyManager.class.getName());


    private NasServerProperyManager ()
    {
        LOGGER.debug("создан NasServerProperyManager");
    }


    public static ServerProperyManager getInstance()
    {
        if (instance == null)
        {
            instance = new NasServerProperyManager();
        }
        return instance;
    }

    @Override public int getPortProperty()
    {
        return -1;
    }

    @Override public String getCloudDirectory()
    {
        return null;
    }

    @Override public List<String> getWelcomeFileList()
    {
        return null;
    }

    @Override public List<String> getWelcomeDirsList()
    {
        return null;
    }


}
