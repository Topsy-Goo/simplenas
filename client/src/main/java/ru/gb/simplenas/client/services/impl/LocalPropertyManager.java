package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.client.services.ClientPropertyManager;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static ru.gb.simplenas.client.CFactory.*;
import static ru.gb.simplenas.common.CommonData.PROPFILE_COMMENT;
import static ru.gb.simplenas.common.CommonData.STR_EMPTY;
import static ru.gb.simplenas.common.Factory.*;

public class LocalPropertyManager implements ClientPropertyManager
{
    private static LocalPropertyManager instance;
    private Properties properties;
    private int port;
    private String hostName;
    private String strLocalPath;
    private String strRemotePath;
    private Path pPropertyFile;
    private boolean needUpdate;
    private int fontSize;
    private static final Logger LOGGER = LogManager.getLogger(LocalPropertyManager.class.getName());


   private LocalPropertyManager ()
    {
        initialize();
    }

    public static ClientPropertyManager getInstance ()
    {
        if (instance == null)
        {
            instance = new LocalPropertyManager();
        }
        return instance;
    }

//-------------------- считывание и запись файла настроек -------------------------------------------------------*/

    @Override public void initialize()
    {
        properties = new Properties();
        pPropertyFile = Paths.get(PROPERTY_FILE_NAME_CLIENT).toAbsolutePath();

        if (Files.exists(pPropertyFile) && Files.isRegularFile(pPropertyFile))
        {
            readAllProperties (pPropertyFile);
        }
        checkPropertiesValues (pPropertyFile);
    }

    private void readAllProperties (@NotNull Path pPropertyFile)
    {
        try (FileInputStream fis = new FileInputStream (pPropertyFile.toString());)
        {
            lnprint("чтение настроек из файла: "+ pPropertyFile.toString());
            properties.loadFromXML(fis);

            String strPort = properties.getProperty (PROPNAME_PORT, String.valueOf(DEFAULT_PORT_NUMBER));
            port = Integer.parseInt(strPort);

            String strFontSize = properties.getProperty (PROPNAME_FONT_SIZE/*, String.valueOf(DEFAULT_FONT_SIZE)*/);
            if (sayNoToEmptyStrings(strFontSize))
                fontSize = Integer.parseInt(strFontSize);

            hostName       = properties.getProperty (PROPNAME_HOST, DEFAULT_HOST_NAME);
            strLocalPath   = properties.getProperty (PROPNAME_PATH_LOCAL, System.getProperty (STR_DEF_FOLDER));
            strRemotePath  = properties.getProperty (PROPNAME_PATH_REMOTE, STR_EMPTY);
        }
        catch (IOException e){e.printStackTrace();}

    }

    private void checkPropertiesValues (@NotNull Path pPropertyFile)
    {
        //boolean needWrite = false;
        if (port <= 0)
        {
            properties.setProperty (PROPNAME_PORT, String.valueOf(DEFAULT_PORT_NUMBER));
            needUpdate = true;
            port = DEFAULT_PORT_NUMBER;
        }
        if (fontSize < MIN_FONT_SIZE)
        {
            properties.setProperty (PROPNAME_FONT_SIZE, String.valueOf(DEFAULT_FONT_SIZE));
            needUpdate = true;
            fontSize = DEFAULT_FONT_SIZE;
        }
        if (!sayNoToEmptyStrings (hostName))
        {
            properties.setProperty (PROPNAME_HOST, DEFAULT_HOST_NAME);
            needUpdate = true;
            hostName = DEFAULT_HOST_NAME;
        }
        if (!sayNoToEmptyStrings (strLocalPath) || !isStringOfRealPath (strLocalPath))
        {
            properties.setProperty (PROPNAME_PATH_LOCAL, System.getProperty (STR_DEF_FOLDER));
            needUpdate = true;
            strLocalPath = System.getProperty (STR_DEF_FOLDER);
        }
        if (strRemotePath == null)
        {
            properties.setProperty (PROPNAME_PATH_REMOTE, STR_EMPTY);
            needUpdate = true;
            strRemotePath = STR_EMPTY;
        }
        if (needUpdate)
            LOGGER.error(String.format("\nНе удалось (частично или полностью) прочитать файл настроек: <%s>", pPropertyFile));
    }

    private boolean writeProperties (@NotNull Path pPropertyFile)
    {
        boolean ok = false;
        try (FileOutputStream fos = new FileOutputStream (pPropertyFile.toString());)
        {
            LOGGER.info(String.format("\nзапись настроек в файл <%s>", pPropertyFile.toString()));
            properties.storeToXML (fos, PROPFILE_COMMENT);
            // (Кирилические символы нормально записываются тоько для XML-файла (исп-ся UTF-8). В текстовом формате они
            // преобразуются в /uxxxx. Это важно особенно для клиента, т.к. имена папок могут содержать символы из разных
            // языков.)
            ok = true;
        }
        catch (IOException e){e.printStackTrace();}
        return ok;
    }

    @Override public void close()
    {
        if (needUpdate)
        if (!createFile (pPropertyFile) || !writeProperties (pPropertyFile))
        {
            LOGGER.error(String.format("\nНе удалось создать/обновить файл настроек <%s>\n", pPropertyFile));
        }
    }

//------------------- гетеры и сетеры ---------------------------------------------------------------------------*/

    @Override public int getFontSize ()     {   return fontSize;   }
    @Override public int getRemotePort()    {   return port;   }
    @Override public String getHostString() {   return hostName;   }
    @Override public String getLastLocalPathString()  {   return strLocalPath;   }
    @Override public String getLastRemotePathString() {   return strRemotePath;   }

    @Override public void setLastLocalPathString (String strLocal)
    {
        if (!sayNoToEmptyStrings (strLocal))
            strLocal = System.getProperty (STR_DEF_FOLDER);

        strLocalPath = strLocal;
        properties.setProperty (PROPNAME_PATH_LOCAL, strLocal);
        needUpdate = true;
    }

    @Override public void setLastRemotePathString (String strRemote)
    {
        if (strRemote == null)
            strRemote = STR_EMPTY;

        strRemotePath = strRemote;
        properties.setProperty (PROPNAME_PATH_REMOTE, strRemote);
        needUpdate = true;
    }
}
//---------------------------------------------------------------------------------------------------------------*/
