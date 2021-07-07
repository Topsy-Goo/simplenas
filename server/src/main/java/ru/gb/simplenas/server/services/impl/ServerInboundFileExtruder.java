package ru.gb.simplenas.server.services.impl;

import ru.gb.simplenas.common.services.impl.InboundFileExtruder;
import ru.gb.simplenas.common.structs.NasMsg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static ru.gb.simplenas.server.SFactory.absolutePathToUserSpace;


public class ServerInboundFileExtruder extends InboundFileExtruder
{
    private ServerInboundFileExtruder instance;

    public ServerInboundFileExtruder () {}

//подготовка к скачиванию файла от клиента
    @Override public boolean initialize (final NasMsg nm, final String userName)
    {
        boolean result = false;
        if (instance == null)
        {
            instance = this;
            Path pRequestedTarget = Paths.get(nm.msg(), nm.fileInfo().getFileName());
            pTargetFile = absolutePathToUserSpace (userName, pRequestedTarget, nm.fileInfo().isDirectory());
            try
            {   if (pTargetFile != null)
                {
                    pTargetDir = pTargetFile.getParent();
                    pTmpDir = Files.createTempDirectory (pTargetDir, null);

                    pFileInTmpFolder = pTmpDir.resolve (nm.fileInfo().getFileName());
                    outputStream = Files.newOutputStream (pFileInTmpFolder, CREATE_NEW, WRITE/*, SYNC*/);
                    result = true;
                }
            }
            catch (IOException e) {e.printStackTrace();}
            finally
            {
                if (!result)
                {
                    cleanup();
                }
                extrudingError = !result;
            }
        }
        return result;
    }


}
//---------------------------------------------------------------------------------------------------------------*/
