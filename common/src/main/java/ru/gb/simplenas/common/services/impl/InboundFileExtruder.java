package ru.gb.simplenas.common.services.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.CommonData;
import ru.gb.simplenas.common.services.FileExtruder;
import ru.gb.simplenas.common.structs.NasMsg;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static ru.gb.simplenas.common.CommonData.WF_;
import static ru.gb.simplenas.common.Factory.print;
import static ru.gb.simplenas.common.Factory.printf;

public class InboundFileExtruder implements FileExtruder {

    protected FileChannel outputFileChannel;
    protected  Path    pTargetFile;   //< полное имя файла назначения
    protected  Path    pTargetDir;    //< папка, куда юзер хочет поместить файл
    protected  Path    pTmpDir;       //< временная папка, где файл будет создан и записан
    protected  Path    pFileInTmpFolder;  //< полное имя временного файла
    protected  boolean extrudingError;
    private static final Logger  LOGGER = LogManager.getLogger(InboundFileExtruder.class.getName());

    public InboundFileExtruder (Path path) {
        if (!initialize (path))
            throw new IllegalArgumentException();
    }

//подготовка к скачиванию файла
    private boolean initialize (Path path) {
        boolean ok = false;
        if (path != null) {
            pTargetFile = path;
            try {
                pTargetDir = pTargetFile.getParent();
                pTmpDir = Files.createTempDirectory (pTargetDir, null);

                pFileInTmpFolder = pTmpDir.resolve (pTargetFile.getFileName());
                outputFileChannel = FileChannel.open (pFileInTmpFolder, CREATE_NEW, WRITE);
                ok = true;
                LOGGER.debug("initializing successfully done");
            }
            catch (IOException e) {e.printStackTrace();}
            finally {
                if (!ok) {
                    extrudingError = true;
                    cleanup();
                }
            }
        }
        return ok;
    }

    @Override public void writeDataBytes2File (byte[] data, int size) throws IOException
    {
        if (!extrudingError && data != null && size > 0) {
            try {
                ByteBuffer bb = ByteBuffer.wrap (data, 0, size);
                outputFileChannel.write (bb);
                print(WF_ + size);
            }
            catch (IOException e) {
                extrudingError = true;
                cleanup();
                throw e;
            }
        }
    }

/** переносим файл из временной папки в папку назначения.  */
    @Override public boolean endupExtruding (NasMsg nm)
    {
        boolean ok = false;
        try {
            if (!extrudingError) {
                Files.move (pFileInTmpFolder, pTargetFile, REPLACE_EXISTING);  //< разрешение на перезапись у нас уже есть
                //поправляем время создания и время изменения
                Files.getFileAttributeView (pTargetFile, BasicFileAttributeView.class)
                     .setTimes (FileTime.from (nm.fileInfo().getModified(), CommonData.FILETIME_UNITS),
                                null,
                                FileTime.from (nm.fileInfo().getCreated(), CommonData.FILETIME_UNITS));
                ok = true;
                printf ("\nзавершено перемещение файла <%s> в папку <%s>\n", pTargetFile.getFileName(), pTargetDir);
            }
        }
        catch (IOException e) {
            extrudingError = true;
            e.printStackTrace();
        }
        finally { cleanup(); }
        return ok;
    }

    @Override public void discard () { extrudingError = true; }

    @Override public void close () { cleanup(); }

    //завершение операции получения файла от клиента + очистка полей, используемых при выполнении такой операции
    protected void cleanup () {
        try {
            if (outputFileChannel != null) {
                outputFileChannel.close();
            }
            if (pFileInTmpFolder != null) Files.deleteIfExists(pFileInTmpFolder);
            if (pTmpDir != null) Files.deleteIfExists(pTmpDir);
        }
        catch (IOException e) {e.printStackTrace();}
        finally {
            pTmpDir = null;
            pTargetDir = null;
            pFileInTmpFolder = null;
            pTargetFile = null;
            outputFileChannel = null;
            LOGGER.debug ("cleanup end");
        }
    }
}
