package ru.gb.simplenas.common.services.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.CommonData;
import ru.gb.simplenas.common.services.FileExtruder;
import ru.gb.simplenas.common.structs.NasMsg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class InboundFileExtruder implements FileExtruder
{
    protected OutputStream outputStream;
    protected Path pTmpDir;
    protected Path pFileInTmpFolder;
    protected Path pTargetDir;
    protected Path pTargetFile;
    protected boolean extrudingError;
    private static final Logger LOGGER = LogManager.getLogger(InboundFileExtruder.class.getName());


    @Override public boolean initialize (final NasMsg nm, final String strData)    {   return false;   }

    @Override public boolean getState() {   return !extrudingError;   }

    @Override public int dataBytes2File (final NasMsg nm)
    {
        int chunks = 0;
        if (!extrudingError)
        {
            byte[] array = (byte[]) nm.data();
            int size = (int) nm.fileInfo().getFilesize();  //< количество байтов для считывания из nm.data.

            if (array != null && size > 0)
            {
                LOGGER.debug("получены данные("+size+").");
                try
                {   outputStream.write (array, 0, size);
                    outputStream.flush();   //< этот flush() частично решил проблему скачивания больших файлов
                    LOGGER.debug("данные записаны.");
                    chunks++;
                }
                catch (IOException e)
                {
                    extrudingError = true;
                    cleanup();
                    e.printStackTrace();
                }
            }
        }
        return chunks;
    }

    @Override public boolean endupExtruding (NasMsg nm)
    {
    //переносим файл из временной папки в папку назначения
        boolean ok = false;
        try
        {
            if (!extrudingError)
            {
                Files.move(pFileInTmpFolder, pTargetFile, REPLACE_EXISTING);  //< разрешение на перезапись у нас уже есть
                //поправляем время создания и время изменения
                Files.getFileAttributeView(pTargetFile, BasicFileAttributeView.class)
                     .setTimes (FileTime.from(nm.fileInfo().getModified(), CommonData.filetimeUnits),
                                null,
                                FileTime.from(nm.fileInfo().getCreated(), CommonData.filetimeUnits));
                ok = true;
            }
        }
        catch (IOException e)
        {
            extrudingError = true;
            e.printStackTrace();
        }
        finally
        {
            cleanup();
        }
        return ok;
    }

    @Override public void discard()    {   extrudingError = true;   }
    @Override public void close()   {   cleanup();  }

//завершение операции получения файла от клиента + очистка полей, используемых при выполнении такой операции
    protected void cleanup()
    {
        try {   if (outputStream != null) { outputStream.flush();   outputStream.close(); }
                if (pFileInTmpFolder != null) Files.deleteIfExists(pFileInTmpFolder);
                if (pTmpDir != null)          Files.deleteIfExists (pTmpDir);
            }
        catch (IOException e){e.printStackTrace();}
        finally
        {
            pTmpDir = null;
            pTargetDir = null;
            pFileInTmpFolder = null;
            pTargetFile = null;
            outputStream = null;
        }
    }

}
