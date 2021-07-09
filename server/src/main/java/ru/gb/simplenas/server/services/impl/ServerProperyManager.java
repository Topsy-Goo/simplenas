package ru.gb.simplenas.server.services.impl;

import ru.gb.simplenas.server.services.ProperyManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static ru.gb.simplenas.common.CommonData.STR_EMPTY;
import static ru.gb.simplenas.common.CommonData.strFileSeparator;
import static ru.gb.simplenas.common.Factory.lnprint;
import static ru.gb.simplenas.server.SFactory.*;

public class ServerProperyManager implements ProperyManager
{
    private static ProperyManager instance;
    private Properties properties;
    private int port = DEFAULT_PUBLIC_PORT_NUMBER;
    private Path pathCloud;
    private List<String> welcomeFiles;   //< файлы, которые должны быть в папке у нового пользователя.
    private List<String> welcomeFolders; //< папки, которые должны быть в папке у нового пользователя.
    private static final String REGEX = "[\\p{Blank}]";   //\p{Punct}

    private ServerProperyManager ()
    {
        initialize();
    }

    public static ProperyManager getInstance()
    {
        if (instance == null)
        {
            instance = new ServerProperyManager();
        }
        return instance;
    }

//-------------------- считывание файла настроек ----------------------------------------------------------------*/

    @Override public void initialize()
    {
        properties = new Properties();
        Path pPropertyFile = Paths.get (PROPERTY_FILE_NAME);

        try (FileInputStream fis = new FileInputStream (pPropertyFile.toString());)
        {
            properties.load(fis);

            pathCloud = readCloudPath();
            port = readPublicPortNumber();
            welcomeFolders = readAsList (PROPNAME_WELCOM_FOLDERS);
            welcomeFiles = readAsList (PROPNAME_WELCOM_FILES);
        }
        catch (IOException e){e.printStackTrace();}
    }

    private List<String> readAsList (String strPropertyName)
    {
        List<String> flist = new ArrayList<>();
        if (strPropertyName != null && !strPropertyName.isEmpty())
        {
            String strFolders = properties.getProperty (strPropertyName, STR_EMPTY);
            String[] arrstrNames = strFolders.split(REGEX);

            for (String s : arrstrNames)
            {
                s = s.trim();
                if (!s.isEmpty())
                    flist.add(s);
            }
        }
        lnprint(String.format("ProperyManager: %s : %s", strPropertyName, flist.toString()));
        return flist;
    }

    private int readPublicPortNumber ()
    {
        String strPort = this.properties.getProperty (PROPNAME_PUBLIC_PORT, String.valueOf(port));
        int result = Integer.parseInt(strPort);
        lnprint(String.format("ProperyManager: %s : %d", PROPNAME_PUBLIC_PORT, result));
        return result;
    }

    private Path readCloudPath ()
    {
        String strCloud = this.properties.getProperty (PROPNAME_CLOUD_NAME, DEFUALT_CLOUD_NAME);
        Path pCloud = Paths.get (strCloud).toAbsolutePath().normalize();

        lnprint(String.format("ProperyManager: %s : <%s>", PROPNAME_CLOUD_NAME, pCloud.toString()));
        return pCloud;
    }

//------------------- гетеры и сетеры ---------------------------------------------------------------------------*/

    @Override public int getPublicPort()    {   return port;   }
    @Override public Path getCloudPath()    {   return pathCloud;   }
    @Override public List<String> getWelcomeFolders()   {   return welcomeFolders;   }
    @Override public List<String> getWelcomeFiles()   {   return welcomeFiles;   }

}//ServerProperyManager
//---------------------------------------------------------------------------------------------------------------*/
        //boolean exists = Files.exists(pCloud);
        //if (!exists)
        //    exists = createCloudFolder (pCloud);
        //
        //if (!exists || !Files.isDirectory (pCloud))
        //    pCloud = null;

        //String s = System.getProperty (STR_DEF_FOLDER);
            //String hostName         = properties.getProperty("HOST", "localhost");
            //String strMRULocalPath  = properties.getProperty("MRU.PATH.LOCAL", s);
            //String strMRUServerPath = properties.getProperty("MRU.PATH.REMOTE", STR_EMPTY);

