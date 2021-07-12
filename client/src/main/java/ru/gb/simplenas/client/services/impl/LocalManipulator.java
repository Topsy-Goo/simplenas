package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.client.services.ClientManipulator;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.annotations.EndupMethod;
import ru.gb.simplenas.common.annotations.ManipulateMethod;
import ru.gb.simplenas.common.services.FileExtruder;
import ru.gb.simplenas.common.services.impl.InboundFileExtruder;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasDialogue;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardOpenOption.READ;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class LocalManipulator implements ClientManipulator {
    private static final String ERROR_OLD_DIALOGUE_STILL_RUNNING = "Не могу начать новый диалог, — предыдущий ещё не закрыт.";
    private static final Logger LOGGER = LogManager.getLogger(LocalManipulator.class.getName());
    private final SocketChannel schannel;
    protected NasCallback callbackChannelActive = this::callbackDummy;
    protected NasCallback callbackMsgIncoming = this::callbackDummy;
    protected NasCallback callbackInfo = this::callbackDummy;
    private NasDialogue dialogue;
    private Map<OperationCodes, Method> mapManipulateMetods, mapEndupMetods;

    public LocalManipulator (NasCallback callbackChannelActive, NasCallback callbackMsgIncoming, NasCallback callbackInfo, SocketChannel sC) {
        this.callbackChannelActive = callbackChannelActive;
        this.callbackMsgIncoming = callbackMsgIncoming;
        this.callbackInfo = callbackInfo;
        this.schannel = sC;
        buildMethodsMaps();
        LOGGER.debug("создан LocalManipulator");
    }

    public static InputStream inputstreamByFilename (String strFolder, String strFileName) {
        InputStream inputstream = null;
        if (sayNoToEmptyStrings(strFolder, strFileName)) {
            Path pLocalFile = Paths.get(strFolder, strFileName).toAbsolutePath().normalize();
            if (!Files.isDirectory(pLocalFile) && Files.exists(pLocalFile)) {
                try {
                    inputstream = Files.newInputStream(pLocalFile, READ);
                }
                catch (IOException e) {e.printStackTrace();}
            }
        }
        return inputstream;
    }

    //---------------------------------------------------------------------------------------------------------------*/

    void callbackDummy (Object... objects) {}

    @Override public void handle (ChannelHandlerContext ctx, NasMsg nm) {
        if (!DEBUG || nm.inbound() == OUTBOUND) {
            if (nm != null) {
                nm.setinbound(INBOUND);
                try {
                    Method m = mapManipulateMetods.get(nm.opCode());
                    m.invoke(this, nm);
                }
                catch (IllegalArgumentException | ReflectiveOperationException e) {e.printStackTrace();}
            }
        }
    }

    //---------------------------------- обработчики простых сообщений ----------------------------------------------*/

    @ManipulateMethod (opcodes = {OK, ERROR}) private void manipulateEndups (NasMsg nm) {
        if (dialogue != null) {
            try {
                Method m = mapEndupMetods.get(dialogue.getTheme());
                m.invoke(this, nm);
            }
            catch (IllegalArgumentException | ReflectiveOperationException e) {e.printStackTrace();}
        }
    }

    @Override public boolean startSimpleRequest (NasMsg nm) {
        boolean result = false;
        if (nm != null && schannel != null && newDialogue(nm)) {
            schannel.writeAndFlush(nm);
            result = true;
        }
        return result;
    }

    //---------------------------------- LIST -----------------------------------------------------------------------*/

    @EndupMethod (opcodes = {CREATE, RENAME, FILEINFO, COUNTITEMS, DELETE, LOAD2SERVER, LOGIN}) private void endupSimpleRequest (NasMsg nm) {
        if (dialogue != null) {
            lnprint("M:пришло сообщение: " + nm.opCode() + "; тема: " + dialogue.getTheme());
            dialogue.add(nm);
            stopTalking(nm);
        }
    }

    @Override public boolean startListRequest (NasMsg nm) {
        boolean done = false;
        if (nm != null && schannel != null && newDialogue(nm, newInfolist())) {
            schannel.writeAndFlush(nm);
            lnprint("M:отправлено сообщение: " + nm.opCode() + "\n");
            done = true;
        }
        return done;
    }

    @ManipulateMethod (opcodes = {LIST}) private void manipulateListQueue (NasMsg nm) {
        if (dialogue != null) {
            print("l");
            if (dialogue.infolist() != null) {
                dialogue.infolist().add(nm.fileInfo());
            }
        }
    }

    //---------------------------------- LOAD2LOCAL -----------------------------------------------------------------*/

    @EndupMethod (opcodes = {LIST}) private void endupListRequest (NasMsg nm) {
        if (dialogue != null) {
            lnprint("M:пришло сообщение: " + nm.opCode() + "; тема: " + dialogue.getTheme());
            dialogue.add(nm);
            nm.setdata(dialogue.infolist());

            if (nm.data() == null) { nm.setdata(newInfolist()); }

            stopTalking(nm);
        }
    }

    @Override public boolean startLoad2LocalRequest (String toLocalFolder, NasMsg nm) {
        boolean result = false;
        if (nm != null && schannel != null && nm.fileInfo() != null) {
            String fileName = nm.fileInfo().getFileName();
            if (sayNoToEmptyStrings(toLocalFolder, fileName, nm.msg())) {
                Path ptargetfile = Paths.get(toLocalFolder, fileName);
                if (newDialogue(nm, new InboundFileExtruder(ptargetfile))) {
                    schannel.writeAndFlush(nm);
                    lnprint("M:отправлено сообщение: " + nm.opCode() + "\n");
                    result = true;
                }
            }
        }
        return result;
    }

    @ManipulateMethod (opcodes = {LOAD2LOCAL}) private void manipulateLoad2LocalQueue (NasMsg nm) {
        if (dialogue != null) {
            dialogue.writeDataBytes2File(nm);
        }
    }

    //---------------------------------- LOAD2SERVER ----------------------------------------------------------------*/

    @EndupMethod (opcodes = {LOAD2LOCAL}) private void endupLoad2LocalRequest (NasMsg nm) {
        lnprint("M:получено сообщение: " + nm.opCode() + "; тема: " + dialogue.getTheme());
        if (dialogue != null) {
            dialogue.add(nm);
            if (nm.opCode() == OK && !dialogue.endupExtruding(nm)) {
                nm.setOpCode(ERROR);
            }
            stopTalking(nm);
        }
    }

    @Override public boolean startLoad2ServerRequest (String fromLocalFolder, NasMsg nm) {
        boolean result = false;
        if (nm != null && nm.fileInfo() != null && schannel != null) {
            InputStream is = inputstreamByFilename(fromLocalFolder, nm.fileInfo().getFileName());
            if (is != null && newDialogue(nm, is)) {
                schannel.writeAndFlush(nm);
                lnprint("M:отправлено сообщение: " + nm.opCode() + "\n");
                result = true;
            }
        }
        return result;
    }

    @ManipulateMethod (opcodes = {LOAD2SERVER}) private void manipulateLoad2ServerQueue (NasMsg nm) {
        if (dialogue != null) {
            dialogue.add(nm);
            boolean result = false;
            try {
                if (dialogue.inputStream() != null && sendFileToServer(nm)) {
                    informOnSuccessfulDataTrabsfer(nm);
                    result = true;
                }
            }
            catch (IOException e) {e.printStackTrace();}
            finally {
                if (!result) { replyWithErrorMessage(); }
            }
        }
    }

    private boolean sendFileToServer (NasMsg nm) throws IOException {
        InputStream istream = null;
        if (nm.fileInfo() == null || dialogue == null || schannel == null || (istream = dialogue.inputStream()) == null) { return false; }

        final long size = nm.fileInfo().getFilesize();
        long read = 0L;
        long rest = size;

        final int bufferSize = (int) Math.min(INT_MAX_BUFFER_SIZE, size);
        byte[] array = new byte[bufferSize];

        nm.setinbound(OUTBOUND);
        nm.setdata(array);

        print("\n");
        while (rest > 0) {
            read = istream.read(array, 0, bufferSize);
            if (read <= 0) { break; }
            rest -= read;
            nm.fileInfo().setFilesize(read);
            print(RF_ + read);
            schannel.writeAndFlush(nm);
            dialogue.incChunks();
        }

        nm.fileInfo().setFilesize(size);
        return rest == 0L;
    }

    //--------------------------------- EXIT ------------------------------------------------------------------------*/

    private void informOnSuccessfulDataTrabsfer (NasMsg nm) {
        if (dialogue != null && schannel != null) {
            nm.setOpCode(OK);
            nm.setinbound(OUTBOUND);
            nm.setdata(null);
            dialogue.add(nm);
            schannel.writeAndFlush(nm);
            lnprint("M:отправлено сообщение: " + nm.opCode() + "; тема: " + dialogue.getTheme());
        }
    }

    @Override public void startExitRequest (NasMsg nm) {
        discardCurrentOperation();
        if (nm == null && schannel != null) {
            nm = new NasMsg(EXIT, OUTBOUND);
            schannel.writeAndFlush(nm);
            lnprint("M:Отправлено сообщение " + OperationCodes.EXIT);
        }
    }

    private void discardCurrentOperation () {
        if (dialogue != null) {
            if (dialogue.getTheme() == LOAD2LOCAL) {
                dialogue.discardExtruding();
            }
        }
    }

    //---------------------------------- общение с InboundHandler'ом ------------------------------------------------*/

    @ManipulateMethod (opcodes = {EXIT}) private void manipulateExitRequest (NasMsg nm) {
        lnprint("M:Получено сообщение " + OperationCodes.EXIT);
        discardCurrentOperation();
        nm.setOpCode(ERROR);
        stopTalking(nm);
    }

    @Override public void onChannelActive (ChannelHandlerContext ctx) {
        lnprint("onChannelActive(): открыто соединение: cts: " + ctx.channel());
        callbackChannelActive.callback();
    }

    @Override public void onChannelInactive (ChannelHandlerContext ctx) {
        lnprint("onChannelInactive(): закрыто соединение: ctx: " + ctx);
    }

    //---------------------------------- dialogue -------------------------------------------------------------------*/

    @Override public void onExceptionCaught (ChannelHandlerContext ctx, Throwable cause) {
        lnprint("onExceptionCaught(): аварийное закрытие соединения: ctx: " + ctx);
    }

    private boolean newDialogue (@NotNull NasMsg nm) {
        if (dialogue != null) {
            lnprint("M:newDialogue(" + nm.opCode() + "): " + ERROR_OLD_DIALOGUE_STILL_RUNNING);
            return false;
        }
        dialogue = new NasDialogue(nm);
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull List<FileInfo> infolist) {
        if (dialogue != null) {
            lnprint("M:newDialogue(" + nm.opCode() + ", infolist): " + ERROR_OLD_DIALOGUE_STILL_RUNNING);
            return false;
        }
        if (infolist != null) {
            dialogue = new NasDialogue(nm, infolist);
        }
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull InputStream inputStream) {
        if (dialogue != null) {
            lnprint("M:newDialogue(" + nm.opCode() + ", is): " + ERROR_OLD_DIALOGUE_STILL_RUNNING);
            return false;
        }
        if (inputStream != null) {
            dialogue = new NasDialogue(nm, inputStream);
        }
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull FileExtruder fe) {
        boolean ok = false;
        if (dialogue != null) {
            lnprint("M:newDialogue(" + nm.opCode() + ", fe, str): " + ERROR_OLD_DIALOGUE_STILL_RUNNING);
            ok = false;
        }
        else if (fe != null) {
            dialogue = new NasDialogue(nm, fe);
            ok = true;
        }
        return ok;
    }

    //---------------------------------- другие полезные методы -----------------------------------------------------*/

    private void stopTalking (NasMsg nm) {
        NasDialogue dlg = dialogue;
        if (dialogue != null) {
            dialogue.close();
            dialogue = null;
        }
        callbackMsgIncoming.callback(nm, dlg);
    }

    public void replyWithErrorMessage () {
        if (dialogue != null && schannel != null) {
            NasMsg nm = new NasMsg(ERROR, STR_EMPTY, OUTBOUND);
            dialogue.add(nm);
            schannel.writeAndFlush(nm);
            lnprint("M:отправлено сообщение: " + nm.opCode() + "; тема: " + dialogue.getTheme());
        }
    }

    private void buildMethodsMaps () {
        mapManipulateMetods = new HashMap<>();
        mapEndupMetods = new HashMap<>();
        Method[] methods = LocalManipulator.class.getDeclaredMethods();

        for (Method m : methods) {
            if (m.isAnnotationPresent(ManipulateMethod.class)) {
                ManipulateMethod annotation = m.getAnnotation(ManipulateMethod.class);
                OperationCodes[] opcodes = annotation.opcodes();
                for (OperationCodes code : opcodes) {
                    mapManipulateMetods.put(code, m);
                }
            }
            if (m.isAnnotationPresent(EndupMethod.class)) {
                EndupMethod annotation = m.getAnnotation(EndupMethod.class);
                OperationCodes[] opcodes = annotation.opcodes();
                for (OperationCodes code : opcodes) {
                    mapEndupMetods.put(code, m);
                }
            }
        }
    }

}
