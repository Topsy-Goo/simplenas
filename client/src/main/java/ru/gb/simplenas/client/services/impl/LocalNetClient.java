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
import ru.gb.simplenas.client.services.ClientManipulator;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.services.impl.NasMsgInboundHandler;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasDialogue;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

import java.nio.file.Paths;

import static ru.gb.simplenas.client.Controller.messageBox;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.lnprint;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class LocalNetClient implements NetClient {
    private static final Logger LOGGER = LogManager.getLogger(LocalNetClient.class.getName());
    private final Thread javafx;
    private final Object syncObj = new Object();
    private final Object syncObj4ConnectionOnly = new Object();
    private final int port;
    private final String hostName;
    private SocketChannel schannel;
    private Channel channelOfChannelFuture;
    private Thread threadNetWork;
    private String userName;
    private ClientManipulator manipulator;
    private NasCallback callbackOnDisconnection = this::callbackDummy;
    private boolean connected = false;
    private NasMsg nmSyncResult;
    private NasDialogue closedDialogue;


    public LocalNetClient (NasCallback cbDisconnection, int port, String hostName) {
        callbackOnDisconnection = cbDisconnection;
        javafx = Thread.currentThread();
        this.port = port;
        this.hostName = hostName;
        LOGGER.debug("создан LocalNetClient");
    }

    //----------------------- колбэки для связи с манипулятором -----------------------------------------------------*/

    void callbackDummy (Object... objects) {}

    void callbackOnChannelActive (Object... objects) {
        synchronized (syncObj) {
            syncObj.notifyAll();
        }
    }

    void callbackOnMsgIncoming (Object... objects) {
        synchronized (syncObj) {
            if (objects != null) {
                int count = objects.length;
                if (count > 0) { nmSyncResult = (NasMsg) objects[0]; }
                if (count > 1) { closedDialogue = (NasDialogue) objects[1]; }
                syncObj.notify();//All
            }
        }
    }

    void callbackInfo (Object... objects) {
        NasMsg nm = (NasMsg) objects[0];
        if (nm != null && nm.opCode() == EXIT) {
            Platform.runLater(()->{
                messageBox(CFactory.ALERTHEADER_CONNECTION, PROMPT_CONNECTION_GETTING_CLOSED, Alert.AlertType.WARNING);
            });
        }
    }

    //------------------------------- подключение, отключение, … ----------------------------------------------------*/

    @Override public boolean connect () {
        boolean isOnAir = schannel != null && schannel.isOpen();

        if (isOnAir) { messageBox(CFactory.ALERTHEADER_CONNECTION, "Уже установлено.", Alert.AlertType.INFORMATION); }
        else {
            synchronized (syncObj4ConnectionOnly) {
                schannel = null;
                threadNetWork = new Thread(this);
                threadNetWork.start();
                try {
                    syncObj4ConnectionOnly.wait(10000);
                }
                catch (InterruptedException e) {e.printStackTrace();}
                isOnAir = schannel != null;
            }
        }
        return isOnAir;
    }

    @Override public void run () {
        NasCallback callbackChannelActive = this::callbackOnChannelActive;
        NasCallback callbackMsgIncoming = this::callbackOnMsgIncoming;
        NasCallback callbackInfo = this::callbackInfo;

        EventLoopGroup groupWorker = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(groupWorker).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel (SocketChannel socketChannel) {
                    schannel = socketChannel;
                    manipulator = new LocalManipulator(callbackChannelActive, callbackMsgIncoming, callbackInfo, socketChannel);
                    socketChannel.pipeline().addLast(new ObjectEncoder(), new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.weakCachingConcurrentResolver(null)), new NasMsgInboundHandler(manipulator));
                }
            });

            ChannelFuture cfuture = b.connect(hostName, port).sync();
            this.channelOfChannelFuture = cfuture.channel();
            this.connected = true;

            lnprint("\n\t\t*** Connected (" + port + "). ***\n");

            synchronized (syncObj4ConnectionOnly) {
                syncObj4ConnectionOnly.notify();
            }
            cfuture.channel().closeFuture().sync();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            groupWorker.shutdownGracefully();
            disconnect();
            synchronized (syncObj4ConnectionOnly) {
                syncObj4ConnectionOnly.notify();
            }
        }
    }

    @Override public void disconnect () {
        if (connected) { callbackOnDisconnection.callback(); }

        if (schannel != null && schannel.isOpen()) {
            sendExitMessage();
            schannel.disconnect();
        }
        if (channelOfChannelFuture != null && channelOfChannelFuture.isOpen()) {
            ChannelFuture cf = channelOfChannelFuture.closeFuture();
        }

        lnprint("\n\t\t*** Disconnected. ***\n");

        connected = false;
        schannel = null;
        channelOfChannelFuture = null;
        threadNetWork = null;
        manipulator = null;
        userName = null;
    }

    //------------------------------- команды для запросов к серверу ------------------------------------------------*/

    @Override public NasMsg login (@NotNull String username) {
        NasMsg result = null;
        if (sayNoToEmptyStrings(username)) {
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(new NasMsg(OperationCodes.LOGIN, username, OUTBOUND))) {
                    try {
                        while (nmSyncResult == null) { syncObj.wait(); }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
            if (result == null) {
                result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, INBOUND);
            }
            else if (result.opCode() == OperationCodes.OK) {
                this.userName = result.msg();
            }
        }
        return result;
    }

    @Override public @NotNull NasMsg list (@NotNull String folder, String... subfolders) {
        NasMsg result = null;
        if (sayNoToEmptyStrings(folder)) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg(LIST, Paths.get(folder, subfolders).toString(), OUTBOUND);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startListRequest(nm)) {
                    try {
                        while (nmSyncResult == null) { syncObj.wait(); }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        return result;
    }

    @Override public @NotNull NasMsg create (@NotNull String newfoldername) {
        NasMsg result = null;
        if (sayNoToEmptyStrings(newfoldername)) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg(OperationCodes.CREATE, newfoldername, OUTBOUND);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) { syncObj.wait(); }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        return result;
    }

    @Override public @NotNull NasMsg rename (@NotNull FileInfo old, @NotNull String newName) {
        NasMsg result = null;
        if (sayNoToEmptyStrings(newName) && old != null) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg(OperationCodes.RENAME, newName, OUTBOUND);
            nm.setfileInfo(old);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) { syncObj.wait(); }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        return result;
    }

    @Override public @NotNull NasMsg transferFile (@NotNull String strLocalFolder, @NotNull String strServerFolder, @NotNull FileInfo fileInfo, OperationCodes opcode) {
        NasMsg result = null;
        if (fileInfo != null && sayNoToEmptyStrings(strLocalFolder, fileInfo.getFileName()) && !fileInfo.isDirectory()) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg(opcode, strServerFolder, OUTBOUND);

            nm.setfileInfo(fileInfo);
            synchronized (syncObj) {
                boolean ok = false;
                if (opcode == LOAD2LOCAL) {
                    ok = manipulator.startLoad2LocalRequest(strLocalFolder, nm);
                }
                else if (opcode == LOAD2SERVER) {
                    ok = manipulator.startLoad2ServerRequest(strLocalFolder, nm);
                }
                if (ok) {
                    nmSyncResult = null;
                    try {
                        while (nmSyncResult == null) { syncObj.wait(); }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        return result;
    }

    @Override public FileInfo fileInfo (@NotNull String folder, @NotNull String fileName) {
        FileInfo result = null;
        if (sayNoToEmptyStrings(folder, fileName)) {
            NasMsg nm = new NasMsg(FILEINFO, folder, OUTBOUND);
            nm.setfileInfo(new FileInfo(fileName, NOT_FOLDER, EXISTS));
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) { syncObj.wait(); }
                        result = nmSyncResult.fileInfo();
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        return result;
    }

    @Override public int countFolderEntries (String strParent, final FileInfo fi) {
        int result = -1;
        if (sayNoToEmptyStrings(strParent)) {
            NasMsg nm = new NasMsg(OperationCodes.COUNTITEMS, strParent, OUTBOUND);
            nm.setfileInfo(fi);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) { syncObj.wait(); }
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
        return result;
    }

    @Override public @NotNull NasMsg delete (String strParent, final FileInfo fi) {
        NasMsg result = null;
        if (sayNoToEmptyStrings(strParent)) {
            result = new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg(OperationCodes.DELETE, strParent, OUTBOUND);
            nm.setfileInfo(fi);
            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) { syncObj.wait(); }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }
        }
        return result;
    }

    @Override public @NotNull NasMsg goTo (@NotNull String folder, String... subfolders) {
        if (folder != null) {
            return list(folder, subfolders);
        }
        return new NasMsg(ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
    }


    void sendExitMessage () {
        if (schannel != null && schannel.isOpen()) {
            manipulator.startExitRequest(null);
        }
    }

}
