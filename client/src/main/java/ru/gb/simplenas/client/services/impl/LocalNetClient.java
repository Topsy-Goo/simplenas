package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.bootstrap.Bootstrap;
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
import ru.gb.simplenas.client.services.ClientManipulator;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.services.impl.NasMsgInboundHandler;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasDialogue;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import static ru.gb.simplenas.client.CFactory.ALERTHEADER_CONNECTION;
import static ru.gb.simplenas.client.Controller.messageBox;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.lnprint;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;
import static ru.gb.simplenas.common.structs.OperationCodes.*;
import static sun.misc.Version.print;

public class LocalNetClient implements NetClient {

    private static final Logger LOGGER = LogManager.getLogger(LocalNetClient.class.getName());
    private        final Object syncObj                = new Object();
    private        final Object syncObj4ConnectionOnly = new Object();
    private              SocketChannel       schannel;
    //private              Channel             channelOfChannelFuture;  << идентичен schannel
    private              Thread              threadNetWork;
    private              String              userName;
    private        final ClientManipulator manipulator;
    private              NasCallback       callbackOnNetClientDisconnection = this::callbackDummy;
    private              boolean           connected  = false;
    private        final int    port;
    private        final String hostName;
    private              NasMsg       nmSyncResult;    //< для передачи сообщений между синхронизированными потоками
    private              NasDialogue  closedDialogue;


    public LocalNetClient (NasCallback cbDisconnection, int p, String hName)
    {
        callbackOnNetClientDisconnection = cbDisconnection;
        port = p;
        hostName = hName;
        manipulator = new LocalManipulator (this::callbackOnChannelActive,
                                            this::callbackOnMsgIncoming,
                                            this::callbackInfo);
        //LOGGER.debug("создан LocalNetClient");
    }

    @Override public boolean isConnected () { return connected; }

//----------------------- колбэки для связи с манипулятором ------------------------------------

    void callbackDummy (Object... objects) {}

/** Обработка окончания установления соединения : теперь мы можем воспользоваться сохранённой переменной
 типа SocketChannel и продолжить авторизацию в методе LocalNetClient.login(). */
    void callbackOnChannelActive (Object... objects)
    {
        synchronized (syncObj) {
            syncObj.notifyAll();
        }
    }

/** Вызывается из манипулятора. Сейчас юзер и поток javafx ждут окончания операции, которую они нам поручили, и
 результат этой операции они получат в nmSyncResult.
 */
    void callbackOnMsgIncoming (Object... objects)
    {
        synchronized (syncObj) {
            if (objects != null) {
                int count = objects.length;
                if (count > 0) nmSyncResult = (NasMsg) objects[0];
                if (count > 1) closedDialogue = (NasDialogue) objects[1];
                syncObj.notifyAll();
            }
        }
    }

/** Вызывается из манипулятора. Служит для информирования юзером о событиях.  */
    void callbackInfo (Object... objects)
    {
        if (objects != null) {
            String header        = (objects.length > 0) ? (String) objects[0] : "Ошибка!";
            String text          = (objects.length > 1) ? (String) objects[1] : ERROR_UNABLE_TO_PERFORM;
            Alert.AlertType type = (objects.length > 2) ? (Alert.AlertType) objects[2] : Alert.AlertType.NONE;

            Platform.runLater(()->messageBox (header, text, type));
        }
    }
//------------------------------- подключение, отключение, … ---------------------------------------

// Запускаем поток, который соединяется с сервером.
    @Override public boolean connect ()
    {
        boolean onAir = schannel != null && schannel.isOpen();

        if (onAir)
            messageBox (ALERTHEADER_CONNECTION, "Уже установлено.", Alert.AlertType.INFORMATION);
        else {
            synchronized (syncObj4ConnectionOnly) {
                schannel = null;
                threadNetWork = new Thread (this);
                threadNetWork.setDaemon(true);
                threadNetWork.start();
                try {
                    syncObj4ConnectionOnly.wait();
                }
                catch (InterruptedException e) { e.printStackTrace(); }
                onAir = schannel != null;
            }//sync
        }
        return onAir;
    }

    @Override public void run ()
    {
        ChannelFuture cfuture = null;
        EventLoopGroup groupWorker = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group (groupWorker)
             .channel (NioSocketChannel.class)
             //.option (ChannelOption.SO_KEEPALIVE, true)
             .handler (new ChannelInitializer<SocketChannel>()
             {
                 @Override protected void initChannel (SocketChannel sc) {
                     schannel = sc;
                     manipulator.setSocketChannel (sc);
                     sc.pipeline()
                       .addLast (//new StringDecoder(), new StringEncoder(),
                                 //An encoder which serializes a Java object into a ByteBuf.
                                 new ObjectEncoder(),
                                 //A decoder which deserializes the received ByteBufs into Java objects.
                                 //new ObjectDecoder (ClassResolvers.cacheDisabled (null)),
                                 //new ObjectDecoder (MAX_OBJECT_SIZE, ClassResolvers.cacheDisabled (null)),
                                 new ObjectDecoder (Integer.MAX_VALUE,
                                                    ClassResolvers.weakCachingConcurrentResolver(null)),
                                                    new NasMsgInboundHandler (manipulator)
                                                    //new TestInboundHandler (manipulator)
                                                   );
                 }
             });
//Одинаковыми оказались:
//      SocketChannel sC,
//      cfuture.channel(),
//      LocalManipulator.channelActive().ctx.channel

            cfuture = b.connect (hostName, port).sync();
            connected = true;

            lnprint("\n\t\t*** Connected (" + port + "). ***\n");

            synchronized (syncObj4ConnectionOnly) {   // это продолжит исполнение LocalNetClient.connect(), если соединение установлено.
                syncObj4ConnectionOnly.notify();
            }
            cfuture.channel().closeFuture().sync(); //< чтобы перешагнуть через эту строчку, нужно сделать
        }                                           //  socketChannel.close().
        catch (Exception e) { e.printStackTrace(); }
        finally {
            groupWorker.shutdownGracefully();
            inlineCleanUpOnDisconnection();
            synchronized (syncObj4ConnectionOnly) {   // это продолжит исполнение LocalNetClient.connect(), если сервера нет.
                syncObj4ConnectionOnly.notify();
            }
        }
    }

/** Убираем за собой после закрытия соединения с сервером. */
    private void inlineCleanUpOnDisconnection ()
    {
        int scPort = (schannel != null) ? schannel.remoteAddress().getPort() : -1;
        if (connected)
            callbackOnNetClientDisconnection.callback(); //< делаем что-то в контроллере
        connected = false;
        manipulator.setSocketChannel (null);
        schannel = null;
        userName = null;
        threadNetWork = null;
        lnprint("\n\t\t*** Disconnected (" + scPort + "). ***\n");
    }

/** Метод вызываться из Controller.closeSession(), т.е. когда юзер закрывает приложжение,
    не разорвав соединение с сервером.  */
    @Override public void disconnect ()
    {
        if (connected) {
            sendExitMessageToServer();
            schannel.close();
        }
    }
//------------------------------- команды для запросов к серверу ------------------------------------------------*/

    @Override public NasMsg login (String login, String password)
    {
        NasMsg result = null;
        if (sayNoToEmptyStrings (login, password)) {

            synchronized (syncObj) {
                NasMsg nm = new NasMsg (NM_OPCODE_LOGIN, login, OUTBOUND);
                nm.setdata (password);
                nmSyncResult = null;

                if (manipulator.startSimpleRequest (nm)) {
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) { e.printStackTrace(); }
                }
            }//sync
            if (result == null) {
                result = new NasMsg (NM_OPCODE_ERROR, ERROR_UNABLE_TO_PERFORM, INBOUND);
            }
            else if (result.opCode() == OperationCodes.NM_OPCODE_OK) {
                this.userName = result.msg();
            }
        }
        return result;
    }

    @Override public @NotNull NasMsg list (@NotNull String folder, String... subfolders)
    {
        NasMsg result = null;
        if (sayNoToEmptyStrings(folder)) {

            result = new NasMsg (NM_OPCODE_ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg (NM_OPCODE_LIST, Paths.get(folder, subfolders).toString(), OUTBOUND);

            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startListRequest(nm)) {
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg create (@NotNull String newfoldername)
    {
        NasMsg result = null;
        if (sayNoToEmptyStrings(newfoldername)) {

            result = new NasMsg (NM_OPCODE_ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg (OperationCodes.NM_OPCODE_CREATE, newfoldername, OUTBOUND);

            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg rename (@NotNull FileInfo old, @NotNull String newName)
    {
        NasMsg result = null;
        if (sayNoToEmptyStrings(newName) && old != null) {

            result = new NasMsg (NM_OPCODE_ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg (OperationCodes.NM_OPCODE_RENAME, newName, OUTBOUND);
            nm.setfileInfo(old);

            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg upload (@NotNull String strLocalFolder,
                                             @NotNull String strServerFolder,
                                             @NotNull FileInfo fileInfo)
    {   NasMsg result = null;
        if (fileInfo != null
        && sayNoToEmptyStrings (strLocalFolder, fileInfo.getFileName())
        && !fileInfo.isDirectory())
        {
            result = new NasMsg (NM_OPCODE_LOAD2SERVER, strServerFolder, OUTBOUND);
            result.setfileInfo (fileInfo);

            synchronized (syncObj) {
                if (manipulator.startLoad2ServerRequest (strLocalFolder, result)) {
                    nmSyncResult = null;
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg download (@NotNull String strLocalFolder,
                                               @NotNull String strServerFolder,
                                               @NotNull FileInfo fileInfo)
    {   NasMsg result = null;
        if (fileInfo != null
        && sayNoToEmptyStrings(strLocalFolder, fileInfo.getFileName())
        && !fileInfo.isDirectory())
        {
            result = new NasMsg (NM_OPCODE_LOAD2LOCAL, strServerFolder, OUTBOUND);
            result.setfileInfo (fileInfo);

            synchronized (syncObj) {
                if (manipulator.startLoad2LocalRequest (strLocalFolder, result)) {
                    nmSyncResult = null;
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public FileInfo fileInfo (@NotNull String folder, @NotNull String fileName)
    {
        FileInfo result = null;
        if (sayNoToEmptyStrings(folder, fileName)) {

            NasMsg nm = new NasMsg (NM_OPCODE_FILEINFO, folder, OUTBOUND);
            nm.setfileInfo(new FileInfo(fileName, NOT_FOLDER, EXISTS));

            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm)) {
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        result = nmSyncResult.fileInfo();
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

//посчёт элементов указанного удалённого каталога. -1 означает ошибку.
    @Override public long countFolderEntries (String strParent, final FileInfo fi)
    {
        long result = -1;
        if (sayNoToEmptyStrings(strParent)) {
            NasMsg nm = new NasMsg (OperationCodes.NM_OPCODE_COUNTITEMS, strParent, OUTBOUND);
            nm.setfileInfo(fi);

            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest (nm)) {
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        nm = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                }
            }//sync
            if (nm.opCode() != NM_OPCODE_ERROR)
                result = nm.fileInfo().getFilesize(); //< результат передаётся в поле FileInfo.filesize.
        }
        return result;
    }

    @Override public @NotNull NasMsg delete (String strParent, final FileInfo fi)
    {
        NasMsg result = null;
        if (sayNoToEmptyStrings(strParent)) {
            result = new NasMsg (NM_OPCODE_ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);

            NasMsg nm = new NasMsg (OperationCodes.NM_OPCODE_DELETE, strParent, OUTBOUND);
            nm.setfileInfo(fi);

            synchronized (syncObj) {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest (nm)) {
                    try {
                        while (nmSyncResult == null) syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e) { e.printStackTrace(); }
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg goTo (@NotNull String folder, String... subfolders)
    {
        if (folder != null)
            return list(folder, subfolders);
        return new NasMsg (NM_OPCODE_ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
    }

/** Отправляем серверу сообщене EXIT  */
    void sendExitMessageToServer ()
    {
        if (schannel != null && schannel.isOpen())
            manipulator.startExitRequest();
    }
}
