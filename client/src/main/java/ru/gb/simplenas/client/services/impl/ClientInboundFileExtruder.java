package ru.gb.simplenas.client.services.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.services.impl.InboundFileExtruder;
import ru.gb.simplenas.common.structs.NasMsg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

public class ClientInboundFileExtruder extends InboundFileExtruder {
    private static final Logger LOGGER = LogManager.getLogger(ClientInboundFileExtruder.class.getName());
    private ClientInboundFileExtruder instance;

    public ClientInboundFileExtruder () {
        LOGGER.debug("создан ClientInboundFileExtruder");
    }


    public boolean initialize (NasMsg nm, String toLocalFolder) {
        boolean result = false;
        if (instance == null) {
            instance = this;
            try {
                pTargetDir = Paths.get(toLocalFolder);
                pTmpDir = Files.createTempDirectory(pTargetDir, null);

                pFileInTmpFolder = pTmpDir.resolve(nm.fileInfo().getFileName());
                outputStream = Files.newOutputStream(pFileInTmpFolder, CREATE_NEW, WRITE);

                pTargetFile = pTargetDir.resolve(nm.fileInfo().getFileName());
                result = true;
            }
            catch (IOException e) {e.printStackTrace();}
            finally {
                if (!result) {
                    cleanup();
                }
                extrudingError = !result;
            }

        }
        return result;
    }

}
