package ru.gb.simplenas.common.services.impl;

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
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static ru.gb.simplenas.common.CommonData.WF_;
import static ru.gb.simplenas.common.Factory.print;

public class InboundFileExtruder implements FileExtruder {
    protected OutputStream outputStream;
    protected Path pTmpDir;
    protected Path pFileInTmpFolder;
    protected Path pTargetDir;
    protected Path pTargetFile;
    protected boolean extrudingError;

    private InboundFileExtruder instance;

    public InboundFileExtruder (Path ptargetfile) {
        if (ptargetfile != null) { initialize(ptargetfile); }
        else { throw new IllegalArgumentException(); }
    }


    @Override public boolean initialize (Path ptargetfile) {
        boolean result = false;
        if (instance == null && ptargetfile != null) {
            instance = this;
            this.pTargetFile = ptargetfile;
            try {
                pTargetDir = pTargetFile.getParent();
                pTmpDir = Files.createTempDirectory(pTargetDir, null);

                pFileInTmpFolder = pTmpDir.resolve(pTargetFile.getFileName());
                outputStream = Files.newOutputStream(pFileInTmpFolder, CREATE_NEW, WRITE/*, SYNC*/);
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


    @Override public boolean getState () { return !extrudingError; }

    @Override public int writeDataBytes2File (final NasMsg nm) {
        int chunks = 0;
        if (!extrudingError) {
            byte[] array = (byte[]) nm.data();
            int size = (int) nm.fileInfo().getFilesize();

            if (array != null && size > 0) {
                try {
                    outputStream.write(array, 0, size);
                    outputStream.flush();
                    print(WF_ + size);
                    chunks++;
                }
                catch (IOException e) {
                    extrudingError = true;
                    cleanup();
                    e.printStackTrace();
                }
            }
        }
        return chunks;
    }

    @Override public boolean endupExtruding (NasMsg nm) {
        boolean ok = false;
        try {
            if (!extrudingError) {
                Files.move(pFileInTmpFolder, pTargetFile, REPLACE_EXISTING);

                Files.getFileAttributeView(pTargetFile, BasicFileAttributeView.class).setTimes(FileTime.from(nm.fileInfo().getModified(), CommonData.filetimeUnits), null, FileTime.from(nm.fileInfo().getCreated(), CommonData.filetimeUnits));
                ok = true;
            }
        }
        catch (IOException e) {
            extrudingError = true;
            e.printStackTrace();
        }
        finally {
            cleanup();
        }
        return ok;
    }

    @Override public void discard () { extrudingError = true; }

    @Override public void close () { cleanup(); }

    protected void cleanup () {
        try {
            if (outputStream != null) {
                outputStream.flush();
                outputStream.close();
            }
            if (pFileInTmpFolder != null) { Files.deleteIfExists(pFileInTmpFolder); }
            if (pTmpDir != null) { Files.deleteIfExists(pTmpDir); }
        }
        catch (IOException e) {e.printStackTrace();}
        finally {
            pTmpDir = null;
            pTargetDir = null;
            pFileInTmpFolder = null;
            pTargetFile = null;
            outputStream = null;
        }
    }

}
