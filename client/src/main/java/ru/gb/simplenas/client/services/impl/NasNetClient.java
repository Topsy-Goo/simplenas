package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.client.CFactory;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.services.Manipulator;
import ru.gb.simplenas.common.services.impl.NasMsgInboundHandler;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

import java.nio.file.Paths;

import static ru.gb.simplenas.client.Controller.messageBox;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.safeRelativeLevelUpStringFrom;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class NasNetClient implements NetClient {
    private static final Logger LOGGER = LogManager.getLogger(NasNetClient.class.getName());
    private final Thread javafx;
    private final NasCallback callbackOnDisconnection;
    private final Object syncObj = new Object();
    private final Object syncObj4ConnectionOnly = new Object();
    private SocketChannel schannel;
    private Channel channelOfChannelFuture;
    private Thread threadNetWork;
    private String userName;
    private Manipulator manipulator;
    private boolean disconnected = false;
    private NasMsg nmSyncResult;


    public NasNetClient (NasCallback cbDisconnection) {
        callbackOnDisconnection = cbDisconnection;
        javafx = Thread.currentThread();
        LOGGER.debug("создан NasNetClient");
    }

    void callbackOnChannelActive (Object... objects) {
        synchronized (syncObj) {
            LOGGER.debug("callbackOnChannelActive(): *********** соединение установлено");
            syncObj.notifyAll();
        }
    }

    void callbackOnMsgIncoming (Object... objects) {
        synchronized (syncObj) {
            nmSyncResult = (NasMsg) objects[0];
            LOGGER.trace(">>>> callbackOnMsgIncoming");
            syncObj.notify();
        }
    }

    void callbackInfo (Object... objects) {
        NasMsg nm = (NasMsg) objects[0];
        if (nm != null && nm.opCode() == EXIT) {
            LOGGER.trace(">>>> callbackInfo");
            Platform.runLater(()->{
                messageBox(CFactory.ALERTHEADER_CONNECTION, PROMPT_CONNECTION_GETTING_CLOSED, Alert.AlertType.WARNING);
            });
        }
    }

    @Override public boolean connect () {
        LOGGER.debug("connect() start");
        boolean isOnAir = schannel != null && schannel.isOpen();
        disconnected = false;

        if (isOnAir) {
            messageBox(CFactory.ALERTHEADER_CONNECTION, "Уже установлено.", Alert.AlertType.INFORMATION);
        }
        else {
            LOGGER.debug("connect() запускаем run-поток и 10 сек. ждём syncObj4ConnectionOnly");
            synchronized (syncObj4ConnectionOnly) {
                schannel = null;
                threadNetWork = new Thread(this);
                threadNetWork.start();
                try {
                    syncObj4ConnectionOnly.wait(10000);
                }
                catch (InterruptedException e) {e.printStackTrace();}
                LOGGER.debug("connect() дождались syncObj4ConnectionOnly");
                isOnAir = schannel != null;
            }
        }
        LOGGER.debug("connect() end");
        return isOnAir;
    }

    @Override public void run () {
        LOGGER.debug("run() start");
        NasCallback callbackChannelActive = this::callbackOnChannelActive;
        NasCallback callbackMsgIncoming = this::callbackOnMsgIncoming;
        NasCallback callbackInfo = this::callbackInfo;

        EventLoopGroup groupWorker = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(groupWorker).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel (SocketChannel socketChannel) throws Exception {
                    schannel = socketChannel;
                    manipulator = new ClientManipulator(callbackChannelActive, callbackMsgIncoming, callbackInfo, socketChannel);
                    socketChannel.pipeline().addLast(new ObjectEncoder(), new ObjectDecoder(ClassResolvers.cacheDisabled(null)), new NasMsgInboundHandler(manipulator));
                    LOGGER.debug("initChannel(): schannel = " + schannel);
                }
            });

            LOGGER.debug("run() вызывается  b.connect (SERVER_ADDRESS, PORT).sync()");
            ChannelFuture cfuture = b.connect(SERVER_ADDRESS, PORT).sync();
            channelOfChannelFuture = cfuture.channel();

            LOGGER.debug("run() в try освобождаем  syncObj4ConnectionOnly");
            synchronized (syncObj4ConnectionOnly) {
                syncObj4ConnectionOnly.notify();
            }
            cfuture.channel().closeFuture().sync();
        }
        catch (Exception e) {
            LOGGER.error("run() : connection interrupted abnormally");
            e.printStackTrace();
        }
        finally {
            groupWorker.shutdownGracefully();
            disconnect();
            LOGGER.debug("run() в finally освобождаем  syncObj4ConnectionOnly");
            synchronized (syncObj4ConnectionOnly) {
                syncObj4ConnectionOnly.notify();
            }
            LOGGER.debug("run(): end");
        }
    }


    @Override public void disconnect () {
        LOGGER.trace("disconnect() start");
        if (disconnected) { return; }
        disconnected = true;

        if (callbackOnDisconnection != null) {
            callbackOnDisconnection.callback();
        }
        if (schannel != null && schannel.isOpen()) {
            sendExitMessage();
            schannel.disconnect();
            schannel.close();
            LOGGER.trace("disconnect() закрывается schannel: " + schannel);
        }
        else { LOGGER.trace("disconnect() schannel уже закрыт"); }

        if (channelOfChannelFuture != null && channelOfChannelFuture.isOpen()) {
            ChannelFuture cf = channelOfChannelFuture.closeFuture();
            LOGGER.trace("disconnect() закрывается channelOfChannelFuture: " + cf);
        }
        else { LOGGER.trace("disconnect() channelOfChannelFuture уже закрыт"); }

        schannel = null;
        channelOfChannelFuture = null;
        threadNetWork = null;
        manipulator = null;
        userName = null;
        LOGGER.trace("disconnect() end");
    }

    @Override public NasMsg login (@NotNull String username) {
        LOGGER.trace("login(): start");
        NasMsg result = null;
        if (sayNoToEmptyStrings(username)) {
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(new NasMsg(OperationCodes.LOGIN, username, false))) {
                    try {
                        while (nmSyncResult == null) {
                            syncObj.wait();
                        }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
            if (result == null) {
                result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, true);
            }
            else if (result.opCode() == OperationCodes.OK) {
                this.userName = result.msg();
            }
        }
        LOGGER.trace("login(): end");
        return result;
    }

    @Override public @NotNull NasMsg list (@NotNull String folder, String... subfolders) {
        LOGGER.trace("list(): start");
        NasMsg result = null;
        if (folder != null) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, false);
            NasMsg nm = new NasMsg(LIST, Paths.get(folder, subfolders).toString(), false);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startListRequest(nm)) {
                    try {
                        while (nmSyncResult == null) {
                            syncObj.wait();
                        }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        LOGGER.trace("list(): end");
        return result;
    }

    @Override public @NotNull NasMsg create (@NotNull String newfoldername) {
        LOGGER.trace("create() start");
        NasMsg result = null;
        if (sayNoToEmptyStrings(newfoldername)) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, false);
            NasMsg nm = new NasMsg(OperationCodes.CREATE, newfoldername, false);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) {
                            syncObj.wait();
                        }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        LOGGER.trace("\t\tNasNetClient.create() end.");
        return result;
    }

    @Override public @NotNull NasMsg rename (@NotNull FileInfo old, @NotNull String newName) {
        LOGGER.trace("rename() start");
        NasMsg result = null;
        if (sayNoToEmptyStrings(newName) && old != null) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, false);
            NasMsg nm = new NasMsg(OperationCodes.RENAME, newName, false);
            nm.setfileInfo(old);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) {
                            syncObj.wait();
                        }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        LOGGER.trace("rename() end");
        return result;
    }

    @Override public @NotNull NasMsg transferFile (@NotNull String strLocal, @NotNull String strRemote, @NotNull FileInfo fileInfo, OperationCodes opcode) {
        LOGGER.trace("transferFile() start");
        NasMsg result = null;
        if (fileInfo != null && sayNoToEmptyStrings(strLocal, fileInfo.getFileName()) && !fileInfo.isDirectory()) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, false);
            NasMsg nm = new NasMsg(opcode, strRemote, false);
            nm.setfileInfo(fileInfo);
            synchronized (syncObj) {
                boolean ok = false;
                if (opcode == LOAD2LOCAL) {
                    ok = manipulator.startLoad2LocalRequest(strLocal, nm);
                }
                else if (opcode == LOAD2SERVER) {
                    ok = manipulator.startLoad2ServerRequest(strLocal, nm);
                }
                if (ok) {
                    nmSyncResult = null;
                    try {
                        while (nmSyncResult == null) {
                            syncObj.wait();
                        }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        LOGGER.trace("transferFile() end");
        return result;
    }

    @Override public FileInfo fileInfo (@NotNull String folder, @NotNull String fileName) {
        FileInfo result = null;
        LOGGER.trace("fileInfo() start");
        if (sayNoToEmptyStrings(folder, fileName)) {
            NasMsg nm = new NasMsg(FILEINFO, folder, false);
            nm.setfileInfo(new FileInfo(fileName, false, true));
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) {
                            syncObj.wait();
                        }
                        result = nmSyncResult.fileInfo();
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        LOGGER.trace("fileInfo() end");
        return result;
    }

    @Override public int countFolderEntries (String strParent, final FileInfo fi) {
        LOGGER.trace("countFolderEntries() start");
        int result = -1;
        if (sayNoToEmptyStrings(strParent)) {
            NasMsg nm = new NasMsg(OperationCodes.COUNTITEMS, strParent, false);
            nm.setfileInfo(fi);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) {
                            syncObj.wait();
                        }
                        nm = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
            if (nm.opCode() != ERROR) {
                result = (int) nm.fileInfo().getFilesize();
            }
        }
        LOGGER.trace("countFolderEntries() end");
        return result;
    }

    @Override public @NotNull NasMsg delete (String strParent, final FileInfo fi) {
        LOGGER.trace("delete() start");
        NasMsg result = null;
        if (sayNoToEmptyStrings(strParent)) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, false);
            NasMsg nm = new NasMsg(OperationCodes.DELETE, strParent, false);
            nm.setfileInfo(fi);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) {
                            syncObj.wait();
                        }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        LOGGER.trace("delete() end");
        return result;
    }

    @Override public @NotNull NasMsg goTo (@NotNull String folder, String... subfolders) {
        if (folder != null) {
            return list(folder, subfolders);
        }
        return new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, false);
    }

    @Override public NasMsg levelUp (@NotNull String strFolder) {
        NasMsg result = null;
        if (strFolder != null) {
            String strParent = safeRelativeLevelUpStringFrom(userName, strFolder);
            if (sayNoToEmptyStrings(strParent)) {
                result = list(strParent);
            }
        }
        return result;
    }

    void sendExitMessage () {
        if (schannel != null && schannel.isOpen()) {
            manipulator.startExitRequest(null);
        }
    }

}
