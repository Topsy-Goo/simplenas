package ru.gb.simplenas.common.structs;

import com.sun.istack.internal.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.Factory;
import ru.gb.simplenas.common.services.FileExtruder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static ru.gb.simplenas.common.CommonData.DEBUG;

public class NasDialogue {
    private static final Logger LOGGER = LogManager.getLogger(NasDialogue.class.getName());
    private final Deque<NasMsg> conversation;
    private Path target;
    private long chunks;
    private FileExtruder fileExtruder;
    private List<FileInfo> infolist;
    private InputStream inputStream;


    private NasDialogue () {
        conversation = new LinkedList<>();
        chunks = 0L;
        LOGGER.debug("создан NasDialogue");
    }

    public NasDialogue (@NotNull NasMsg nm) {
        this();
        if (nm == null) { throw new IllegalArgumentException(); }
        conversation.add(nm);
    }

    public NasDialogue (@NotNull NasMsg nm, @NotNull FileExtruder tc) {
        this(nm);
        if (tc == null) { throw new IllegalArgumentException(); }
        fileExtruder = tc;
    }

    public NasDialogue (@NotNull NasMsg nm, @NotNull List<FileInfo> infolist) {
        this(nm);
        this.infolist = infolist;
    }

    public NasDialogue (@NotNull NasMsg nm, @NotNull InputStream inputStream) {
        this(nm);
        this.inputStream = inputStream;
    }

    public boolean add (NasMsg nm) {
        if (nm == null) {
            return false;
        }
        NasMsg copy = Factory.nmcopy(nm);
        return conversation.add(copy);
    }

    public OperationCodes getTheme () { return conversation.getFirst().opCode(); }

    public long getChunks () { return chunks; }

    public void incChunks () { chunks++; }

    public List<FileInfo> infolist () { return infolist; }

    public InputStream inputStream () { return inputStream; }

    public boolean initializeFileExtruder (NasMsg nm, String userName) {
        if (fileExtruder != null) {
            return fileExtruder.initialize(nm, userName);
        }
        return false;
    }

    public boolean transferStateIsOk () { return fileExtruder.getState(); }

    public void dataBytes2File (NasMsg nm) {
        if (fileExtruder != null) {
            chunks = fileExtruder.dataBytes2File(nm);
        }
    }

    public boolean endupExtruding (NasMsg nm) {
        boolean ok = false;
        if (fileExtruder != null) {
            ok = fileExtruder.endupExtruding(nm);
        }
        return ok;
    }

    public void discardExtruding () {
        if (fileExtruder != null) {
            fileExtruder.discard();
        }
    }

    public void cleanupFileExtruder () {
        fileExtruder.close();
        fileExtruder = null;
    }

    public void close () {
        if (conversation != null) { conversation.clear(); }
        if (fileExtruder != null) { cleanupFileExtruder(); }

        try {
            if (inputStream != null) { inputStream.close(); }
        }
        catch (IOException e) {e.printStackTrace();}
        finally {
            LOGGER.debug("удален NasDialogue");
        }
    }

    protected void finalize () throws Throwable {
        if (DEBUG) {
            super.finalize();
            LOGGER.trace("_______A dialogue object is being processed by GC right now._______");
        }
    }
}
