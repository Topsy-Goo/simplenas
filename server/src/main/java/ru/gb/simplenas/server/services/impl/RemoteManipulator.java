package ru.gb.simplenas.server.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.annotations.EndupMethod;
import ru.gb.simplenas.common.annotations.ManipulateMethod;
import ru.gb.simplenas.common.services.FileExtruder;
import ru.gb.simplenas.common.services.Manipulator;
import ru.gb.simplenas.common.services.impl.InboundFileExtruder;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasDialogue;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;
import ru.gb.simplenas.server.SFactory;
import ru.gb.simplenas.server.services.ServerFileManager;

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
import static ru.gb.simplenas.server.SFactory.*;

public class RemoteManipulator implements Manipulator {
    private static final Logger LOGGER = LogManager.getLogger(RemoteManipulator.class.getName());
    private final ServerFileManager sfm;
    private SocketChannel socketChannel;
    private String userName;
    private Path pathCurrentAbsolute;
    private NasDialogue dialogue;
    private Map<OperationCodes, Method> mapManipulateMetods, mapEndupMetods;
    private boolean canLoadToLoacal;

    //---------------------------------------------------------------------------------------------------------------*/

    public RemoteManipulator (ServerFileManager sfm, SocketChannel socketChannel) {
        this.sfm = sfm;
        this.socketChannel = socketChannel;
        buildMethodsMaps();
        LOGGER.debug("создан RemoteManipulator");
    }

    @Override public void handle (@NotNull ChannelHandlerContext ctx, @NotNull NasMsg nm) {
        if (ctx != null && checkMethodInvocationContext4Handle(nm)) {
            nm.setinbound(INBOUND);
            try {
                Method m = mapManipulateMetods.get(nm.opCode());
                if (m.getParameterCount() == 1) { m.invoke(this, nm); }
                else if (m.getParameterCount() == 2) { m.invoke(this, nm, ctx); }
            }
            catch (IllegalArgumentException | ReflectiveOperationException e) {LOGGER.error("handle(): ", e);}
        }
    }

    private boolean checkMethodInvocationContext4Handle (NasMsg nm) {
        if (mapManipulateMetods != null) {
            if (nm != null) {
                if (nm.opCode() == LOGIN || (sayNoToEmptyStrings(userName) && pathCurrentAbsolute != null)) {
                    if (!DEBUG || nm.inbound() == OUTBOUND) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    //------------------------------- LOAD2SERVER -------------------------------------------------------------------*/

    @ManipulateMethod (opcodes = {OK, ERROR}) private void manipulateEndups (NasMsg nm) {
        if (mapEndupMetods != null && dialogue != null) {
            try {
                Method m = mapEndupMetods.get(dialogue.getTheme());
                m.invoke(this, nm);
            }
            catch (IllegalArgumentException | ReflectiveOperationException e) {e.printStackTrace();}
        }
    }

    @ManipulateMethod (opcodes = {LOAD2SERVER}) private void manipulateLoad2ServerRequest (NasMsg nm) {
        if (nm.fileInfo() == null || !sayNoToEmptyStrings(nm.msg(), nm.fileInfo().getFileName())) {
            LOGGER.error("manipulateFileInfoRequest(): Illegal Arguments");
        }
        else if (nm.data() == null) {
            Path ptargetfile = getSafeTargetFilePath(nm);
            if (ptargetfile != null) {
                if (newDialogue(nm, new InboundFileExtruder(ptargetfile))) {
                    informClientWithOperationCode(nm, LOAD2SERVER);
                }
                else {
                    replyWithErrorMessage(null);
                    stopTalking();
                }
            }
        }
        else { dialogue.writeDataBytes2File(nm); }
    }

    private Path getSafeTargetFilePath (NasMsg nm) {
        Path ptargetfile = null;
        if (nm != null && nm.fileInfo() != null) {
            String fileName = nm.fileInfo().getFileName();
            String folderName = nm.msg();

            if (sayNoToEmptyStrings(userName, folderName, fileName)) {
                Path pRequestedTarget = Paths.get(folderName, fileName);
                ptargetfile = sfm.absolutePathToUserSpace(userName, pRequestedTarget, nm.fileInfo().isDirectory());
            }
        }
        return ptargetfile;
    }

    private void informClientWithOperationCode (NasMsg nm, OperationCodes opcode) {
        if (socketChannel != null) {
            nm.setOpCode(opcode);
            nm.setinbound(OUTBOUND);
            if (dialogue != null) {
                dialogue.add(nm);
            }
            socketChannel.writeAndFlush(nm);
        }
    }

    //------------------------------- LOAD2LOCAL --------------------------------------------------------------------*/

    @EndupMethod (opcodes = {LOAD2SERVER}) private void endupLoad2ServerRequest (NasMsg nm) {
        boolean ok = false;
        if (dialogue != null) {
            dialogue.add(nm);
            if (nm.opCode() == OK) {
                if (dialogue.endupExtruding(nm)) {
                    informOnSuccessfulDataTrabsfer(nm);
                    ok = true;
                }
            }
        }
        if (!ok) { replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM + " endupLoad2ServerRequest"); }
        stopTalking();
    }

    @ManipulateMethod (opcodes = {LOAD2LOCAL}) private void manipulateLoad2LocalRequest (NasMsg nm) {
        if (nm.fileInfo() == null || !sayNoToEmptyStrings(nm.msg(), nm.fileInfo().getFileName())) { return; }

        boolean result = false;
        canLoadToLoacal = true;

        FileInfo fi = nm.fileInfo();
        Path p = Paths.get(nm.msg(), fi.getFileName());
        Path valid = sfm.absolutePathToUserSpace(userName, p, fi.isDirectory());
        String errMsg = "Не удалось прочитать указанный файл. ";

        nm.setfileInfo(valid != null ? new FileInfo(valid) : null);

        if (valid != null) {
            if (nm.fileInfo() == null) { errMsg += "Некорректное имя файла."; }
            else if (!nm.fileInfo().isExists()) { errMsg += "Файл не существует."; }
            else if (nm.fileInfo().isDirectory()) { errMsg += "Файл является папкой."; }
            else if (!Files.isReadable(valid)) { errMsg += "Отказано в доступе."; }
            else {
                try (InputStream is = Files.newInputStream(valid, READ)) {
                    result = sendFileToClient(nm, is);
                }
                catch (IOException e) {e.printStackTrace();}
            }
        }
        if (result) { informOnSuccessfulDataTrabsfer(nm); }
        else { replyWithErrorMessage(errMsg); }
    }

    private boolean sendFileToClient (NasMsg nm, InputStream istream) throws IOException {
        boolean result = false;
        if (istream == null || !canLoadToLoacal || nm.fileInfo() == null || socketChannel == null) { return false; }

        final long size = nm.fileInfo().getFilesize();
        long read = 0L;
        long rest = size;

        final int bufferSize = (int) Math.min(INT_MAX_BUFFER_SIZE, size);
        byte[] array = new byte[bufferSize];

        nm.setinbound(OUTBOUND);
        nm.setdata(array);

        print("\n");
        while (rest > 0 && canLoadToLoacal) {
            read = istream.read(array, 0, bufferSize);
            if (read <= 0) { break; }
            rest -= read;
            print(RF_ + read);
            nm.fileInfo().setFilesize(read);
            socketChannel.writeAndFlush(nm);
        }
        nm.fileInfo().setFilesize(size);
        result = canLoadToLoacal && rest == 0L;
        return result;
    }

    //---------------------------- LOGIN, FILEINFO, COUNTITEMS, CREATE, RENAME, DELETE ------------------------------*/

    @ManipulateMethod (opcodes = {LOGIN}) private boolean manipulateLoginRequest (@NotNull NasMsg nm, ChannelHandlerContext ctx) {
        if (userName != null) {
            LOGGER.debug("manipulateLoginRequest(): userName = " + userName);
        }
        boolean result = false;
        String name = nm.msg();
        String errMsg = ERROR_UNABLE_TO_PERFORM;

        if (!isNameValid(name)) {
            errMsg = String.format(ERR_FORMAT_UNALLOWABLE_USERNAME, name);
        }
        else if (!SFactory.clientsListAdd(this, name)) {
            errMsg = String.format(ERR_FORMAT_LOGIN_REJECTED, name);
        }
        else {
            pathCurrentAbsolute = sfm.constructAbsoluteUserRoot(name);
            if (sfm.checkUserFolder(name) && pathCurrentAbsolute != null) {
                userName = name;
                informClientWithOperationCode(nm, OK);
                result = true;
            }
        }
        if (!result) {
            replyWithErrorMessage(errMsg);
            socketChannel.close();
            ctx.disconnect();
        }
        return result;
    }

    @ManipulateMethod (opcodes = {OperationCodes.FILEINFO}) private void manipulateFileInfoRequest (NasMsg nm) {
        boolean ok = false;
        if (nm.fileInfo() != null && sayNoToEmptyStrings(nm.msg(), nm.fileInfo().getFileName())) {
            FileInfo fi = sfm.getSafeFileInfo(userName, nm.msg(), nm.fileInfo().getFileName());
            if (fi != null) {
                nm.setfileInfo(fi);
                informClientWithOperationCode(nm, OK);
                ok = true;
            }
        }
        if (!ok) { replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM); }
    }

    @ManipulateMethod (opcodes = {OperationCodes.COUNTITEMS}) private void manipulateCountEntriesRequest (NasMsg nm) {
        int result = -1;
        if (nm.fileInfo() != null && sayNoToEmptyStrings(nm.msg(), nm.fileInfo().getFileName())) {
            Path p = Paths.get(nm.msg(), nm.fileInfo().getFileName());
            Path valid = sfm.absolutePathToUserSpace(userName, p, nm.fileInfo().isDirectory());

            if (valid != null) {
                result = sfm.safeCountDirectoryEntries(valid, userName);
            }
            if (result >= 0) {
                nm.fileInfo().setFilesize(result);
                informClientWithOperationCode(nm, OK);
            }
        }
        if (result < 0) { replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM); }
    }

    @ManipulateMethod (opcodes = {OperationCodes.CREATE}) private void manipulateCreateRequest (NasMsg nm) {
        boolean ok = false;
        if (sayNoToEmptyStrings(nm.msg())) {
            nm.setfileInfo(sfm.createSubfolder4User(pathCurrentAbsolute, userName, nm.msg()));
            if (nm.fileInfo() != null) {
                nm.setmsg(sfm.relativizeByUserName(userName, pathCurrentAbsolute).toString());
                informClientWithOperationCode(nm, OK);
                ok = true;
            }
        }
        if (!ok) { replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM); }
    }

    @ManipulateMethod (opcodes = {OperationCodes.RENAME}) private void manipulateRenameRequest (NasMsg nm) {
        boolean ok = false;
        if (sayNoToEmptyStrings(nm.msg()) && nm.fileInfo() != null) {
            String newName = nm.msg();
            FileInfo fi = sfm.safeRename(pathCurrentAbsolute, nm.fileInfo().getFileName(), newName, userName);
            if (fi != null) {
                nm.setmsg(sfm.relativizeByUserName(userName, pathCurrentAbsolute).toString());
                informClientWithOperationCode(nm, OK);
                ok = true;
            }
        }
        if (!ok) { replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM); }
    }

    @ManipulateMethod (opcodes = {OperationCodes.DELETE}) private void manipulateDeleteRequest (NasMsg nm) {
        boolean result = false;
        if (nm.fileInfo() != null && sayNoToEmptyStrings(nm.msg(), nm.fileInfo().getFileName())) {
            Path p = Paths.get(nm.msg(), nm.fileInfo().getFileName());
            Path valid = sfm.absolutePathToUserSpace(userName, p, nm.fileInfo().isDirectory());

            if (result = valid != null && sfm.safeDeleteFileOrDirectory(valid, userName)) {
                informClientWithOperationCode(nm, OK);
            }
        }
        if (!result) { replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM); }
    }

    //---------------------------------- LIST -----------------------------------------------------------------------*/

    @ManipulateMethod (opcodes = {LIST}) private void manipulateListRequest (@NotNull NasMsg nm) {
        if (socketChannel != null) {
            Path valid = sfm.absolutePathToUserSpace(userName, Paths.get(nm.msg()), FOLDER);
            if (valid != null && Files.exists(valid)) {
                pathCurrentAbsolute = valid;
                sendFileInfoList(listFolderContents(valid));

                nm.setmsg(sfm.relativizeByUserName(userName, valid).toString());
                informClientWithOperationCode(nm, OK);
            }
            else { replyWithErrorMessage(SFactory.ERROR_INVALID_FILDER_SPECIFIED); }
        }
    }

    private void sendFileInfoList (@NotNull List<FileInfo> flist) {
        if (socketChannel != null && flist != null) {
            NasMsg newnm = new NasMsg(LIST, null, OUTBOUND);
            int counter = 0;
            for (FileInfo fi : flist) {
                newnm.setfileInfo(fi);
                counter++;
                socketChannel.writeAndFlush(newnm);
            }
            LOGGER.debug("sendFileInfoList(): отправлено сообщений: " + counter);
        }
    }


    //---------------------------------------------------------------------------------------------------------------*/

    private void informOnSuccessfulDataTrabsfer (NasMsg nm) {
        nm.setdata(null);
        informClientWithOperationCode(nm, OK);
    }

    public void replyWithErrorMessage (String errMsg) {
        if (socketChannel != null) {
            if (errMsg == null) {
                errMsg = ERROR_SERVER_UNABLE_TO_PERFORM;
            }
            NasMsg nm = new NasMsg(ERROR, errMsg, OUTBOUND);

            if (dialogue != null) {
                dialogue.add(nm);
            }
            socketChannel.writeAndFlush(nm);
        }
    }

    private void newDialogue (@NotNull NasMsg nm) {
        if (dialogue != null) {
            replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM);
        }
        else if (nm != null) {
            dialogue = new NasDialogue(nm);
        }
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull FileExtruder fextruder) {
        boolean ok = false;
        if (dialogue != null) {
            replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM);
        }
        else if (fextruder != null) {
            dialogue = new NasDialogue(nm, fextruder);
            ok = true;
        }
        return ok;
    }

    private void stopTalking () {
        if (dialogue != null) {
            dialogue.close();
        }
        dialogue = null;
    }

    @Override public void onChannelActive (@NotNull ChannelHandlerContext ctx) {
        LOGGER.info("onChannelActive(): открыто соединение: ctx: " + ctx);
        if (socketChannel == null) {
            socketChannel = (SocketChannel) ctx.channel();
        }
    }

    @Override public void onChannelInactive (@NotNull ChannelHandlerContext ctx) {
        LOGGER.info("onChannelInactive(): закрыто соединение: ctx: " + ctx);
        if (userName != null) {
            clientsListRemove(this, userName);
            userName = null;
        }
    }

    @Override public void onExceptionCaught (@NotNull ChannelHandlerContext ctx, @NotNull Throwable cause) {
        LOGGER.info("onExceptionCaught(): аварийное закрытие соединения: ctx: " + ctx);
        if (userName != null) {
            clientsListRemove(this, userName);
            userName = null;
        }
    }

    //----------------------------------- EXIT ----------------------------------------------------------------------*/

    @Override public void startExitRequest (NasMsg nm) {
        if (socketChannel != null) {
            discardCurrentOperation();
            if (nm == null) { nm = new NasMsg(OperationCodes.EXIT, PROMPT_CONNECTION_GETTING_CLOSED, OUTBOUND); }

            socketChannel.writeAndFlush(nm);
            socketChannel.disconnect();
            LOGGER.info("Отправлено сообщение " + OperationCodes.EXIT);
        }
    }

    @ManipulateMethod (opcodes = {OperationCodes.EXIT}) private void manipulateExitRequest (NasMsg nm, ChannelHandlerContext ctx) {
        LOGGER.info("Получено сообщение " + OperationCodes.EXIT);
        if (sayNoToEmptyStrings(userName) && socketChannel != null) {
            discardCurrentOperation();
            if (userName != null) { clientsListRemove(this, userName); }
            userName = null;
            socketChannel.disconnect();
            ctx.disconnect();
        }
    }

    private void discardCurrentOperation () {
        if (dialogue != null) {
            switch (dialogue.getTheme()) {
                case LOAD2SERVER:
                    dialogue.discardExtruding();
                    replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM);
                    break;
                case LOAD2LOCAL:
                    canLoadToLoacal = false;
                    break;
            }
            stopTalking();
        }
    }

    private void buildMethodsMaps () {
        mapManipulateMetods = new HashMap<>();
        mapEndupMetods = new HashMap<>();
        Method[] methods = RemoteManipulator.class.getDeclaredMethods();

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

