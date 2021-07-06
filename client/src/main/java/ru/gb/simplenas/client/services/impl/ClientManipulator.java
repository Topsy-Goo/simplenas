package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.annotations.EndupMethod;
import ru.gb.simplenas.common.annotations.ManipulateMethod;
import ru.gb.simplenas.common.services.FileExtruder;
import ru.gb.simplenas.common.services.Manipulator;
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
import static ru.gb.simplenas.common.Factory.newInfolist;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class ClientManipulator implements Manipulator {
    static final String ERROR_OLD_DIALOGUE_STILL_RUNNING = "Cannot start new dialogue - the previous one slill in use.";
    private static final Logger LOGGER = LogManager.getLogger(ClientManipulator.class.getName());
    private final SocketChannel schannel;
    protected NasCallback callbackChannelActive = this::callbackDummy;
    protected NasCallback callbackMsgIncoming = this::callbackDummy;
    protected NasCallback callbackInfo = this::callbackDummy;
    private NasDialogue dialogue;
    private Map<OperationCodes, Method> mapManipulateMetods, mapEndupMetods;


    public ClientManipulator (NasCallback callbackChannelActive, NasCallback callbackMsgIncoming, NasCallback callbackInfo, SocketChannel sC) {
        this.callbackChannelActive = callbackChannelActive;
        this.callbackMsgIncoming = callbackMsgIncoming;
        this.callbackInfo = callbackInfo;
        this.schannel = sC;
        new Thread(this::buildMethodMaps).start(); // javafx must die !!!
        LOGGER.debug("создан ClientManipulator");
    }

    public static InputStream inputstreamByFilename (String strFolder, String strFileName)      //+
    {
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

    void callbackDummy (Object... objects) {}

    @Override public void handle (ChannelHandlerContext ctx, NasMsg nm) {
        LOGGER.trace("\nhandle(): start (" + nm + ")");
        if (!DEBUG || nm.inbound() == false) {
            if (nm != null) {
                nm.setinbound(true);
                try {
                    Method m = mapManipulateMetods.get(nm.opCode());
                    m.invoke(this, nm);
                }
                catch (IllegalArgumentException | ReflectiveOperationException e) {e.printStackTrace();}
            }
        }
        LOGGER.trace("handle(): end (" + nm + ")\n");
    }

    @ManipulateMethod (opcodes = {OK, ERROR}) private void manipulateEndups (NasMsg nm) {
        LOGGER.trace("manipulateEndups(): start (" + nm + ")");
        if (dialogue != null) {
            try {
                Method m = mapEndupMetods.get(dialogue.getTheme());
                m.invoke(this, nm);
            }
            catch (IllegalArgumentException | ReflectiveOperationException e) {e.printStackTrace();}
        }
        LOGGER.trace("manipulateEndups(): end (" + nm + ")");
    }

    @Override public boolean startSimpleRequest (NasMsg nm)     //OUT
    {
        LOGGER.trace("startSimpleRequest(): start (" + nm + ")");
        boolean result = false;
        if (nm != null && schannel != null && newDialogue(nm)) {
            LOGGER.trace("startSimpleRequest(): >>>>>>>>" + nm.opCode() + ">>>>>>> " + nm);
            schannel.writeAndFlush(nm);
            result = true;
        }
        LOGGER.trace("startSimpleRequest(): end (" + nm + ")");
        return result;
    }

    @EndupMethod (opcodes = {CREATE, RENAME, FILEINFO, COUNTITEMS, DELETE, LOAD2SERVER, LOGIN}) private void endupSimpleRequest (NasMsg nm)                 //IN
    {
        LOGGER.trace("endupSimpleRequest(): start (" + nm + ")");
        if (dialogue != null) {
            dialogue.add(nm);
            callbackMsgIncoming.callback(nm);
            stopTalking();
        }
        LOGGER.trace("endupSimpleRequest(): end (" + nm + ")");
    }

    @Override public boolean startListRequest (NasMsg nm) {
        LOGGER.trace("startListRequest(): start (" + nm + ")");
        boolean done = false;
        if (nm != null && schannel != null && newDialogue(nm, newInfolist())) {
            LOGGER.trace("startListRequest(): >>>>>>>>" + nm.opCode() + ">>>>>>> " + nm);
            schannel.writeAndFlush(nm);
            done = true;
        }
        LOGGER.trace("startListRequest(): end (" + nm + ")");
        return done;
    }

    @ManipulateMethod (opcodes = {OperationCodes.LIST}) private void manipulateListQueue (NasMsg nm) {
        LOGGER.trace("manipulateListQueue(): start (" + nm + ")");
        if (dialogue != null) {
            if (dialogue.infolist() != null) {
                dialogue.infolist().add(nm.fileInfo());
                LOGGER.trace(nm.fileInfo().isDirectory() ? "D" : "f");
            }
        }
        LOGGER.trace("manipulateListQueue(): end (" + nm + ")");
    }

    @EndupMethod (opcodes = {LIST}) private void endupListRequest (NasMsg nm) {
        LOGGER.trace("endupListRequest(): start (" + nm + ")");
        if (dialogue != null) {
            dialogue.add(nm);
            nm.setdata(dialogue.infolist());

            if (nm.data() == null) {
                if (DEBUG) { throw new RuntimeException(); }
                nm.setdata(newInfolist());
            }
            callbackMsgIncoming.callback(nm);
            stopTalking();
        }
        LOGGER.trace("endupListRequest(): end (" + nm + ")");
    }

    @Override public boolean startLoad2LocalRequest (String toLocalFolder, NasMsg nm) {
        LOGGER.trace("startLoad2LocalRequest(): start (" + nm + ")");
        boolean result = false;
        if (nm != null && schannel != null) {
            if (newDialogue(nm, new ClientInboundFileExtruder(), toLocalFolder)) {
                LOGGER.trace("startLoad2LocalRequest(): >>>>>>>>" + nm.opCode() + ">>>>>>> " + nm);
                schannel.writeAndFlush(nm);
                result = true;
            }
            if (!result) {
                nm.setOpCode(ERROR);
                stopTalking();
            }
        }
        LOGGER.trace("startLoad2LocalRequest(): end (" + nm + ")");
        return result;
    }

    @ManipulateMethod (opcodes = {OperationCodes.LOAD2LOCAL}) private void manipulateLoad2LocalQueue (NasMsg nm) {
        LOGGER.trace("manipulateLoad2LocalQueue(): start (" + nm + ")");
        if (dialogue != null) {
            dialogue.dataBytes2File(nm);
        }
        LOGGER.trace("manipulateLoad2LocalQueue(): end (" + nm + ")");
    }

    @EndupMethod (opcodes = {LOAD2LOCAL}) private void endupLoad2LocalRequest (NasMsg nm) {
        LOGGER.trace("endupLoad2LocalRequest(): start (" + nm + ")");
        if (dialogue != null) {
            dialogue.add(nm);

            if (DEBUG) {
                FileInfo fi = nm.fileInfo();
                if (nm.opCode() == OK) { LOGGER.debug("ОТДАЧА файла завершена «" + fi.getFileName() + "»: размер=" + fi.getFilesize() + ", chuncks=" + dialogue.getChunks() + ""); }
                else { LOGGER.debug("ОТДАЧА файла завершилась ошибкой"); }
            }

            if (nm.opCode() == OK && !dialogue.endupExtruding(nm)) {
                nm.setOpCode(ERROR);
            }
            callbackMsgIncoming.callback(nm);
            stopTalking();
        }
        LOGGER.trace("endupLoad2LocalRequest(): end (" + nm + ")");
    }

    @Override public boolean startLoad2ServerRequest (String fromLocalFolder, NasMsg nm) {
        LOGGER.trace("startLoad2ServerRequest(): start (" + nm + ")");
        boolean result = false;
        if (nm != null && nm.fileInfo() != null && schannel != null) {
            InputStream is = inputstreamByFilename(fromLocalFolder, nm.fileInfo().getFileName());
            if (is != null && newDialogue(nm, is)) {
                LOGGER.trace("startLoad2ServerRequest(): >>>>>>>>" + nm.opCode() + ">>>>>>> " + nm);
                schannel.writeAndFlush(nm);
                result = dialogue.inputStream() != null;
            }
            if (!result) {
                nm.setOpCode(ERROR);
                stopTalking();
            }
        }
        LOGGER.trace("startLoad2ServerRequest(): end (" + nm + ")");
        return result;
    }

    @ManipulateMethod (opcodes = {OperationCodes.LOAD2SERVER}) private void manipulateLoad2ServerQueue (NasMsg nm) {
        LOGGER.trace("manipulateLoad2ServerQueue(): start (" + nm + ")");
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
        LOGGER.trace("manipulateLoad2ServerQueue(): end (" + nm + ")");
    }

    private boolean sendFileToServer (NasMsg nm) throws IOException {
        if (nm.fileInfo() == null || dialogue == null || schannel == null) {
            return false;
        }
        final long size = nm.fileInfo().getFilesize();
        long read = 0L;
        long rest = size;
        byte[] array = new byte[MAX_BUFFER_SIZE];
        InputStream istream = dialogue.inputStream();

        nm.setinbound(false);
        nm.setdata(array);

        LOGGER.trace("sendFileToServer(): начало пересылки данных>>>>>>>");
        while (rest > 0 && istream != null) {
            read = istream.read(array, 0, MAX_BUFFER_SIZE);
            if (read <= 0) {
                break;
            }
            rest -= read;
            nm.fileInfo().setFilesize(read);
            schannel.writeAndFlush(nm);
            dialogue.incChunks();
        }
        LOGGER.trace("sendFileToServer(): >>>>>>>>конец пересылки данных");

        nm.fileInfo().setFilesize(size);
        return rest == 0L;
    }

    private void informOnSuccessfulDataTrabsfer (NasMsg nm) {
        if (dialogue != null && schannel != null) {
            nm.setOpCode(OK);
            nm.setinbound(false);
            nm.setdata(null);
            dialogue.add(nm);
            LOGGER.trace(" >>>>>>>>" + nm.opCode() + ">>>>>>> " + nm);
            schannel.writeAndFlush(nm);
        }
    }

    @Override public void startExitRequest (NasMsg nm)      //OUT
    {
        LOGGER.trace("startExitRequest(): start (" + nm + ")");
        discardCurrentOperation(nm);
        if (nm == null && schannel != null) {
            nm = new NasMsg(EXIT, false);
            LOGGER.trace("startExitRequest(): >>>>>>>>" + nm.opCode() + ">>>>>>> " + nm);
            schannel.writeAndFlush(nm);
        }
        LOGGER.trace("startExitRequest(): end (" + nm + ")");
    }

    private void discardCurrentOperation (NasMsg nm) {
        LOGGER.trace("discardCurrentOperation(): start");
        if (dialogue != null) {
            if (dialogue.getTheme() == LOAD2LOCAL) {
                dialogue.discardExtruding();
            }

            nm.setOpCode(ERROR);
            callbackMsgIncoming.callback(nm);
            stopTalking();
        }
        LOGGER.trace("discardCurrentOperation(): end");
    }

    @ManipulateMethod (opcodes = {EXIT}) private void manipulateExitRequest (NasMsg nm)          //IN
    {
        LOGGER.trace("manipulateExitRequest(): start (" + nm + ")");
        discardCurrentOperation(nm);
        callbackInfo.callback(nm);
        LOGGER.trace("manipulateExitRequest(): end (" + nm + ")");
    }

    @Override public void onChannelActive (ChannelHandlerContext ctx) {
        LOGGER.info("onChannelActive(): открыто соединение: cts: " + ctx.channel());
        callbackChannelActive.callback();
    }

    @Override public void onChannelInactive (ChannelHandlerContext ctx) {
        LOGGER.info("onChannelInactive(): закрыто соединение: ctx: " + ctx);
    }

    @Override public void onExceptionCaught (ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("onExceptionCaught(): аварийное закрытие соединения: ctx: " + ctx);
    }

    private boolean newDialogue (@NotNull NasMsg nm) {
        LOGGER.trace("newDialogue(nm): start (" + nm + ")(" + dialogue + ")");
        if (dialogue != null) {
            //throw new RuntimeException();
            LOGGER.error("newDialogue(): " + ERROR_OLD_DIALOGUE_STILL_RUNNING);
            LOGGER.trace("newDialogue(nm): end (" + nm + ")");
            return false;
        }
        dialogue = new NasDialogue(nm);
        LOGGER.trace("newDialogue(nm): end (" + nm + ")");
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull List<FileInfo> infolist) {
        LOGGER.trace("newDialogue(il): start (" + nm + ")(" + dialogue + ")");
        if (dialogue != null) {
            LOGGER.error("newDialogue(): " + ERROR_OLD_DIALOGUE_STILL_RUNNING);
            LOGGER.trace("newDialogue(il): end (" + nm + ")");
            return false;
        }
        if (infolist != null) {
            dialogue = new NasDialogue(nm, infolist);
        }
        LOGGER.trace("newDialogue(il): end (" + nm + ")");
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull InputStream inputStream) {
        LOGGER.trace("newDialogue(is): start (" + nm + ")(" + dialogue + ")");
        if (dialogue != null) {
            LOGGER.error("newDialogue(): " + ERROR_OLD_DIALOGUE_STILL_RUNNING);
            LOGGER.trace("newDialogue(is): end (" + nm + ")");
            return false;
        }
        if (inputStream != null) {
            dialogue = new NasDialogue(nm, inputStream);
        }
        LOGGER.trace("newDialogue(is): end (" + nm + ")");
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull FileExtruder fe, @NotNull String toLocalFolder)       //+l
    {
        LOGGER.trace("newDialogue(fe): start (" + nm + ")(" + dialogue + ")");
        boolean ok = false;
        if (dialogue != null) {
            LOGGER.error("newDialogue(): " + ERROR_OLD_DIALOGUE_STILL_RUNNING);
            LOGGER.trace("newDialogue(fe): end (" + nm + ")");
            ok = false;
        }
        else if (fe != null && sayNoToEmptyStrings(toLocalFolder)) {
            dialogue = new NasDialogue(nm, fe);
            ok = dialogue.initializeFileExtruder(nm, toLocalFolder);
        }
        LOGGER.trace("newDialogue(fe): end (" + nm + ")");
        return ok;
    }

    private void stopTalking ()      //+l
    {
        LOGGER.trace("\t\t\t\tstopTalking() call");
        if (dialogue != null) {
            dialogue.close();
            dialogue = null;
        }
    }

    public void replyWithErrorMessage ()     //+l
    {
        LOGGER.trace("replyWithErrorMessage(): start");
        if (dialogue != null && schannel != null) {
            NasMsg nm = new NasMsg(ERROR, STR_EMPTY, false);
            dialogue.add(nm);

            LOGGER.trace("replyWithErrorMessage(): >>>>>>>>" + nm.opCode() + ">>>>>>> " + nm);
            schannel.writeAndFlush(nm);
        }
        LOGGER.trace("replyWithErrorMessage(): end");
    }

    private void buildMethodMaps ()      //+l
    {
        LOGGER.trace("buildMethodMaps(): start");
        mapManipulateMetods = new HashMap<>();
        mapEndupMetods = new HashMap<>();
        Method[] methods = ClientManipulator.class.getDeclaredMethods();

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
        LOGGER.trace("buildMethodMaps(): end");
    }

}
