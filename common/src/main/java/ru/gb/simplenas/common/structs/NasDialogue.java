package ru.gb.simplenas.common.structs;

import org.jetbrains.annotations.NotNull;
import ru.gb.simplenas.common.services.FileExtruder;
import ru.gb.simplenas.common.services.impl.InboundFileExtruder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.nio.file.StandardOpenOption.READ;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;

public class NasDialogue {

    private OperationCodes theme;
    private FileExtruder   fileExtruder;
    private List<FileInfo> infolist;
    private FileChannel    inputFileChannel;
    public final TransferContext tc;
    //private static final Logger LOGGER = LogManager.getLogger(NasDialogue.class.getName());

/* поля класса TransferContext используются только при передаче файла (чтобы не возиться с набором
 переменных, входящих в контекст операции отдачи/приёма файла):
*/
    public class TransferContext {
        private byte[] array;
        private ByteBuffer bb;
        public Path path;
        public long rest;

        public void prepareToUpload (@NotNull NasMsg nm) {
            rest = nm.fileInfo().getFilesize();
            array = new byte [(int) Math.min (INT_MAX_BUFFER_SIZE, rest)];
            bb = ByteBuffer.wrap (array);
        }

        public int fileReadAndUpload (@NotNull NasMsg nm,
                                      @NotNull Function <NasMsg,Void> sendFunction) throws IOException
        {
            int read = -2;
            if (inputFileChannel != null && inputFileChannel.isOpen()) {
                read = inputFileChannel.read ((ByteBuffer) bb.clear());
                if (read > 0) {
                    rest -= read;
                    print (RF_ + read);
    /* Возможна ситуация, когда array велик, а остаток файла, записанный в него, сравнительно
    мал. В этом случае встаёт вопрос: передавать по сети частично заполненный массив, или создать
    новый массив меньшего размера. Попытка решить проблему при пом.
    ByteBuffer.slice() и ByteBuffer.wrap(buf, 0, size) не дали результат, — массив остаётся тот же
    самый и той же длины.
        Наболее разумным кажется вариант: махнуть на это рукой, и заставить принимающую сторону
    внимательнее относиться к значению в NasMsg.FileInfo.size.  */
                    nm.setdata (array);
                    nm.fileInfo().setFilesize (read);
                    sendFunction.apply (nm);
                }
            }
            if (read <= 0)
                throw new IOException (String.format (
                    "Ошибка чтения файла <%s>: считано %d байтов при остатке %d.", path, read, rest));
            return read;
        }

        public boolean fileDownloadAndWrite (@NotNull NasMsg nm) throws IOException {
            if (fileExtruder != null) {
                fileExtruder.writeDataBytes2File ((byte[]) nm.data(), (int) nm.fileInfo().getFilesize());
                return true;
            }
            else if (DEBUG) throw new RuntimeException ("ОШИБКА: NasDialogue.fileExtruder == null.");
            return false;
        }

        public boolean endupDownloading (@NotNull NasMsg nm) {
            return endupExtruding (nm);
        }
    }

//---------------------- Конструирование ----------------------------------------------------*/

    private NasDialogue () {  tc = new TransferContext();  }

    private NasDialogue (@NotNull NasMsg nm) {
        this();
        if (nm == null)
            throw new IllegalArgumentException();
        theme = nm.opCode();
    }

    private NasDialogue (@NotNull NasMsg nm, @NotNull Path path) {
        this(nm);
        fileExtruder = new InboundFileExtruder (path);
        tc.path = path;
    }

    private NasDialogue (@NotNull NasMsg nm, @NotNull List<FileInfo> infolist) {
        this(nm);
        this.infolist = infolist;
    }


    public static NasDialogue NasDialogueForDownloading (@NotNull NasMsg nm, @NotNull Path path) {
        NasDialogue d = new NasDialogue (nm, path);
        return d;
    }

    public static NasDialogue NasDialogueForUploading (@NotNull NasMsg nm, @NotNull Path path) {
        NasDialogue d = null;
        FileChannel fc;
        try {
            fc = FileChannel.open (path, READ);
            if (fc != null) {
                d = new NasDialogue (nm, path);
                d.inputFileChannel = fc;
                fc.position (0L);
            }
        }
        catch (IOException e) { e.printStackTrace(); }
        return d;
    }

    public static NasDialogue NasDialogueForList (@NotNull NasMsg nm) {
        return new NasDialogue (nm, newInfolist());
    }

    public static NasDialogue NasDialogueForSimpleRequest (@NotNull NasMsg nm) {
        return new NasDialogue (nm);
    }

//-------------------------------------------------------------------------------------------*/

    public OperationCodes getTheme () { return theme; } //< подскажет, с чего всё началось

    public List<FileInfo> infolist () { return infolist; }

//------------------------------ методы для работы с FileExtruder'ом -----------------------*/

    public FileExtruder getFileExtruder () {   return fileExtruder;   }

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
//---------------------------------------------------------------------------------------------*/
    public void close () {
        //if (conversation != null) conversation.clear(); <-- само очистится. К тому же
        // в LocalNetClient.callbackOnMsgIncoming это может понадобиться (но пока не понадобилось).
        if (fileExtruder != null) cleanupFileExtruder();
        try {
            if (inputFileChannel != null)
                inputFileChannel.close();   inputFileChannel = null;
        }
        catch (IOException e) {e.printStackTrace();}
    }
}
