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
    private Path pPropertyFile;
    private boolean loaded;
    private boolean needUpdate;

    private int fontSizeDefault;    //< размер шрифта GUI, который используется в отсутствие авторизации
    private int port;
    private String hostName;
    private String strDefaultLocalPath;
    //private String strRemotePath; < больше не используется

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

        if (Files.exists (pPropertyFile) && Files.isRegularFile(pPropertyFile))
        {
            try (FileInputStream fis = new FileInputStream (pPropertyFile.toString());)
            {
                lnprint("чтение настроек из файла: "+ pPropertyFile.toString());
                properties.loadFromXML(fis);
                loaded = true;
                readDefaultProperties (pPropertyFile);
            }
            catch (IOException e){e.printStackTrace();}
        }
        checkDefaultPropertiesValues (pPropertyFile);
    }

    private void readDefaultProperties (@NotNull Path pPropertyFile)
    {
        String strPort = properties.getProperty (PROPNAME_PORT, String.valueOf(DEFAULT_PORT_NUMBER));
        if (sayNoToEmptyStrings (strPort) && isConvertableToInteger (strPort))
        {
            port = Integer.parseInt(strPort);
        }

        String strFontSize = properties.getProperty (PROPNAME_FONT_SIZE/*, String.valueOf(DEFAULT_FONT_SIZE)*/);
        if (sayNoToEmptyStrings (strFontSize) && isConvertableToInteger (strFontSize))
        {
            fontSizeDefault = Integer.parseInt(strFontSize);
        }

        hostName       = properties.getProperty (PROPNAME_HOST, DEFAULT_HOST_NAME);
        strDefaultLocalPath = properties.getProperty(PROPNAME_PATH_LOCAL, System.getProperty(STR_DEF_FOLDER));
        //strRemotePath  = properties.getProperty (PROPNAME_PATH_REMOTE, STR_EMPTY);
    }

    private void checkDefaultPropertiesValues (@NotNull Path pPropertyFile)
    {
        if (port <= 0)
        {
            properties.setProperty (PROPNAME_PORT, String.valueOf(DEFAULT_PORT_NUMBER));
            needUpdate = true;
            port = DEFAULT_PORT_NUMBER;
        }
        if (!isFontSizeValid (fontSizeDefault))
        {
            properties.setProperty (PROPNAME_FONT_SIZE, String.valueOf(DEFAULT_FONT_SIZE));
            needUpdate = true;
            fontSizeDefault = DEFAULT_FONT_SIZE;
        }
        if (!sayNoToEmptyStrings (hostName))
        {
            properties.setProperty (PROPNAME_HOST, DEFAULT_HOST_NAME);
            needUpdate = true;
            hostName = DEFAULT_HOST_NAME;
        }
        if (!sayNoToEmptyStrings(strDefaultLocalPath) || !isStringOfRealPath(strDefaultLocalPath))
        {
            properties.setProperty (PROPNAME_PATH_LOCAL, System.getProperty (STR_DEF_FOLDER));
            needUpdate = true;
            strDefaultLocalPath = System.getProperty(STR_DEF_FOLDER);
        }
        //if (strRemotePath == null)
        //{
        //    properties.setProperty (PROPNAME_PATH_REMOTE, STR_EMPTY);
        //    needUpdate = true;
        //    strRemotePath = STR_EMPTY;
        //}
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

    @Override public int getDefaultFontSize()     {   return fontSizeDefault;   }
    @Override public int getRemotePort()    {   return port;   }
    @Override public String getHostString() {   return hostName;   }
    @Override public String getDefaultLastLocalPathString ()  {   return strDefaultLocalPath;   }
    //@Override public String getLastRemotePathString() {   return strRemotePath;   }

    //(для случаев, когда пользователь не зарегистрировался)
    @Override public void setLastLocalPathString (String strLocalPath)
    {
        if (!sayNoToEmptyStrings (strLocalPath))
            strLocalPath = System.getProperty (STR_DEF_FOLDER);

        this.strDefaultLocalPath = strLocalPath;
        properties.setProperty (PROPNAME_PATH_LOCAL, strLocalPath);
        needUpdate = true;
    }

    //@Override public void setLastRemotePathString (String strRemote)
    //{
    //    if (strRemote == null)
    //        strRemote = STR_EMPTY;
    //
    //    strRemotePath = strRemote;
    //    properties.setProperty (PROPNAME_PATH_REMOTE, strRemote);
    //    needUpdate = true;
    //}

    @Override public int getUserFontSize (@NotNull String userName)
    {
        int userFontSize = DEFAULT_FONT_SIZE;
        boolean ok = false;
        if (loaded && sayNoToEmptyStrings (userName))
        {
            String propName = userPropName (userName, PROPNAME_FONT_SIZE);
            String strFontSize = properties.getProperty (propName/*, String.valueOf(DEFAULT_FONT_SIZE)*/);

            if (isConvertableToInteger (strFontSize))
            {
                userFontSize = Integer.parseInt(strFontSize);
                ok = isFontSizeValid (userFontSize);
            }
            else setUserFontSize (userName, DEFAULT_FONT_SIZE);
        }
        if (!ok)
        {
            userFontSize = DEFAULT_FONT_SIZE;
            needUpdate = true;
        }
        return userFontSize;
    }

    @Override public String getUserLocalPath (@NotNull String userName)
    {
        String result = System.getProperty (STR_DEF_FOLDER);
        if (loaded && sayNoToEmptyStrings (userName))
        {
            String propName = userPropName (userName, PROPNAME_PATH_LOCAL);
            String strLocalPath = properties.getProperty (propName);

            if (sayNoToEmptyStrings (strLocalPath))
                result = strLocalPath;
        }
        return result;
    }

    @Override public String getUserRemotePath (@NotNull String userName)
    {
        String result = STR_EMPTY;
        if (sayNoToEmptyStrings (userName))
        {
            result = userName;
            if (loaded)
            {
                String propName = userPropName (userName, PROPNAME_PATH_REMOTE);
                String strRemotePath = properties.getProperty (propName);

                if (sayNoToEmptyStrings (strRemotePath))
                    result = strRemotePath;
            }
        }
        return result;
    }

    @Override public void setUserFontSize (@NotNull String userName, int size)
    {
        if (sayNoToEmptyStrings (userName) && isFontSizeValid (size))
        {
            String propName = userPropName (userName, PROPNAME_FONT_SIZE);
            properties.setProperty (propName, String.valueOf(size));
            needUpdate = true;
        }
        else LOGGER.error("setUserFontSize()");
    }

    @Override public void setUserLastLocalPath (@NotNull String userName, @NotNull String strPath)
    {
        if (sayNoToEmptyStrings (userName, strPath))
        {
            String propName = userPropName (userName, PROPNAME_PATH_LOCAL);
            properties.setProperty (propName, strPath);
            needUpdate = true;
        }
        else LOGGER.error("setUserLastLocalPath()");
    }

    @Override public void setUserLastRemotePath (@NotNull String userName, @NotNull String strPath)
    {
        if (sayNoToEmptyStrings (userName, strPath))
        {
            String propName = userPropName (userName, PROPNAME_PATH_REMOTE);
            properties.setProperty (propName, strPath);
            needUpdate = true;
        }
        else LOGGER.error("setUserLastRemotePath()");
    }

//---------------------- различные вспомогательные методы -------------------------------------------------------*/

    private static String userPropName (@NotNull String userName, @NotNull String defaultName)
    {
        return sformat("%s.%s", userName, defaultName);
    }

    private static boolean isConvertableToInteger (@NotNull String number)
    {
        boolean ok = sayNoToEmptyStrings(number);
        if (ok)
        for (Character ch : number.toCharArray())
        {
            if (!(ok = Character.isDigit(ch)))
                break;
        }
        return ok;
    }

    private static boolean isFontSizeValid (int fontSize)
    {
        return fontSize >= MIN_FONT_SIZE && fontSize <= MAX_FONT_SIZE;
    }

}
