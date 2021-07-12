package ru.gb.simplenas.server.services.impl;

import com.sun.istack.internal.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.server.services.ServerPropertyManager;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.server.SFactory.*;

public class RemotePropertyManager implements ServerPropertyManager
{
    private static RemotePropertyManager instance;
    private Properties properties;
    private int publicPort;
    private String strPathCloud;
    private List<String> welcomeFiles;   //< файлы, которые должны быть в папке у нового пользователя.
    private List<String> welcomeFolders; //< папки, которые должны быть в папке у нового пользователя.
    private static final String REGEX = "[,;\\p{Blank}]";   //\p{Punct}
    private static final Logger LOGGER = LogManager.getLogger(RemotePropertyManager.class.getName());


    private RemotePropertyManager ()
    {
        initialize();
    }

    public static ServerPropertyManager getInstance ()
    {
        if (instance == null)
        {
            instance = new RemotePropertyManager();
        }
        return instance;
    }

//-------------------- считывание и запись файла настроек -------------------------------------------------------*/

    @Override public void initialize()
    {
        properties = new Properties();
        Path pPropertyFile = Paths.get(PROPERTY_FILE_NAME_SERVER).toAbsolutePath();

        if (Files.exists(pPropertyFile) && Files.isRegularFile(pPropertyFile))
        {
            readAllProperties (pPropertyFile);
        }
        checkPropertiesValues (pPropertyFile);
    }

    private void readAllProperties (@NotNull Path pPropertyFile)
    {
        try (FileInputStream fis = new FileInputStream(pPropertyFile.toString());)
        {
            printf("\nчтение настроек из файла <%s>", pPropertyFile.toString());
            properties.loadFromXML(fis);

            publicPort = readPublicPortNumber();
            strPathCloud = readCloudName();
            welcomeFolders = readAsList (PROPNAME_WELCOM_FOLDERS);
            welcomeFiles = readAsList (PROPNAME_WELCOM_FILES);
        }
        catch (IOException e) {e.printStackTrace();}
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
        printf("ServerPropertyManager: %s : %s", strPropertyName, flist.toString());
        return flist;
    }

    private int readPublicPortNumber()
    {
        String strPort = this.properties.getProperty (PROPNAME_PUBLIC_PORT, String.valueOf(publicPort));
        int result = Integer.parseInt(strPort);
        printf("\nServerPropertyManager: %s : %d", PROPNAME_PUBLIC_PORT, result);
        return result;
    }

    private String readCloudName()
    {
        String strCloudName = this.properties.getProperty (PROPNAME_CLOUD_NAME, DEFUALT_CLOUD_NAME);
        printf("\nServerPropertyManager: %s : <%s>", PROPNAME_CLOUD_NAME, strCloudName);
        return strCloudName;
    }

    private void checkPropertiesValues (@NotNull Path pPropertyFile)
    {
        boolean needWrite = false;
        if (publicPort <= 0)
        {
            properties.setProperty (PROPNAME_PUBLIC_PORT, String.valueOf(DEFAULT_PUBLIC_PORT_NUMBER));
            needWrite = true;
            publicPort = DEFAULT_PUBLIC_PORT_NUMBER;
        }
        if (!sayNoToEmptyStrings (strPathCloud))
        {
            properties.setProperty (PROPNAME_CLOUD_NAME, DEFUALT_CLOUD_NAME);
            needWrite = true;
            strPathCloud = DEFUALT_CLOUD_NAME;
        }
        if (welcomeFolders == null)
        {
            properties.setProperty (PROPNAME_WELCOM_FOLDERS, DEFUALT_WELCOM_FOLDERS_STRING);
            needWrite = true;
            welcomeFolders = new ArrayList<>();
        }
        if (welcomeFiles == null)
        {
            properties.setProperty (PROPNAME_WELCOM_FILES, STR_EMPTY);
            needWrite = true;
            welcomeFiles = new ArrayList<>();
        }
        if (needWrite)
        {
            errprintf("\nНе удалось (частично или полностью) прочитать файл настроек: <%s>\n", pPropertyFile);
            if (!createFile (pPropertyFile) || !writeProperties (pPropertyFile))
            {
                errprintf("\nНе удалось создать/обновить файл настроек <%s>\n", pPropertyFile);
            }
        }
    }

    private boolean writeProperties (@NotNull Path pPropertyFile)
    {
        boolean ok = false;
        try (FileOutputStream fos = new FileOutputStream (pPropertyFile.toString());)
        {
            printf("\nзапись настроек в файл <%s>\n", pPropertyFile.toString());
            properties.storeToXML(fos, PROPFILE_COMMENT);
            // (Кирилические символы нормально записываются тоько для XML-файла (исп-ся UTF-8). В текстовом формате они
            // преобразуются в /uxxxx.)
            ok = true;
        }
        catch (IOException e){e.printStackTrace();}
        return ok;
    }

//------------------- гетеры и сетеры ---------------------------------------------------------------------------*/

    @Override public int getPublicPort()    {   return publicPort;   }
    @Override public String getCloudName()    {   return strPathCloud;   }
    @Override public List<String> getWelcomeFolders()   {   return welcomeFolders;   }
    @Override public List<String> getWelcomeFiles()   {   return welcomeFiles;   }

}
//---------------------------------------------------------------------------------------------------------------*/
