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
import java.util.concurrent.SynchronousQueue;

import static ru.gb.simplenas.client.CFactory.ALERTHEADER_CONNECTION;
import static ru.gb.simplenas.client.Controller.messageBox;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.lnprint;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class LocalNetClient implements NetClient {

    private static final Logger LOGGER = LogManager.getLogger(LocalNetClient.class.getName());
    /** Поток, которому проучено соединение с сервером и обмен данными через установленный канал связи.
    (Запросы к серверу инициируется из основного потока приложения.) */
    private              Thread threadNetWork;
    /** Монитор для обмена данными между потоками — основным и сетевым (threadNetWork). */
    private        final Object netclient2manipulatorMonitor = new Object();
    /** Ссылка на NasMsg-объект, который используется для обмена данными между основным и сетевым потоками. */
    private              NasMsg       nmSyncResult;
    /** Ссылка на NasDialogue-объект, который используется для обмена данными между основным и сетевым потоками. */
    private              NasDialogue  closedDialogue;
    /** Монитор для синхронизации метода LocalNetClient.connect с ходом подключения к серверу. */
    private        final Object connectionMonitor = new Object();
    /** Колбэк для выполнения некоторых действий в контроллере, необходимых при разрыве соединения. */
    private              NasCallback callbackOnNetClientDisconnection = this::callbackDummy;
    /** Статус соединения с сервером. */
    private              boolean connected  = false;
    private              SocketChannel schannel;
    private        final int     port;
    private        final String  hostName;
    private        final ClientManipulator manipulator;


    public LocalNetClient (NasCallback cbDisconnection, int p, String hName)
    {
        callbackOnNetClientDisconnection = cbDisconnection;
        port = p;
        hostName = hName;
        manipulator = new LocalManipulator (this::callbackOnChannelActive,
                                            //this::callbackOnMsgIncoming,
                                            this::callbackInfo/*,
                                            this*/);
    }

    @Override public boolean isConnected () { return connected; }

//----------------------- колбэки для связи с манипулятором ------------------------------------

    void callbackDummy (Object... objects) {}

/** Обработка окончания установления соединения : теперь мы можем воспользоваться сохранённой переменной
 типа SocketChannel и продолжить авторизацию в методе LocalNetClient.login(). */
    void callbackOnChannelActive (Object... objects)
    {
        synchronized (netclient2manipulatorMonitor) {
            netclient2manipulatorMonitor.notifyAll();
        }
    }

/* * Вызывается из манипулятора. Сейчас юзер и поток javafx ждут окончания операции, которую они нам поручили, и
 результат этой операции они получат в nmSyncResult.
 * /
/*    void callbackOnMsgIncoming (Object... objects)
    {
        synchronized (netclient2manipulatorMonitor) {
            if (objects != null) {
                int count = objects.length;
                if (count > 0) nmSyncResult = (NasMsg) objects[0];
                //if (count > 1) closedDialogue = (NasDialogue) objects[1];
                netclient2manipulatorMonitor.notifyAll();
            }
        }
    }*/

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
            synchronized (connectionMonitor) {
                schannel = null;
                threadNetWork = new Thread (this);
                threadNetWork.setDaemon(true);
                threadNetWork.start();
                try {
                    connectionMonitor.wait();
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
                                                    new NasMsgInboundHandler (manipulator));
                 }
             });
            cfuture = b.connect (hostName, port).sync();

            lnprint("\n\t\t*** Connected (" + port + "). ***\n");
            connected = true;
            synchronized (connectionMonitor) {
                connectionMonitor.notify(); //< пробуждаем LocalNetClient.connect().
            }
            cfuture.channel().closeFuture().sync(); //< ожидание вызова SocketChannel.close().
        }
        catch (Exception e) { e.printStackTrace(); }
        finally {
            groupWorker.shutdownGracefully();
            inlineCleanUpOnDisconnection();
            synchronized (connectionMonitor) {
                connectionMonitor.notify(); //< пробуждаем LocalNetClient.connect().
            }
        }
//Одинаковыми оказались:
//      SocketChannel sC,
//      ChannelFuture.channel(),
//      ChannelHandlerContext.channel()
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
            NasMsg nm = new NasMsg (NM_OPCODE_LOGIN, login, OUTBOUND);
            nm.setdata (password);

            if (manipulator.startSimpleRequest (nm))
            try {
                result = (NasMsg) NM_OPCODE_LOGIN.getSynque().take(); //< ждём ответ манипулятора.
            }
            catch (InterruptedException e) { e.printStackTrace(); }

            if (result == null)
                result = new NasMsg (NM_OPCODE_ERROR, ERROR_UNABLE_TO_PERFORM, INBOUND);
        }
        return result;
    }

/** Запрашиваем список элементов удалённой папки. */
    @Override public @NotNull NasMsg list (String folder, String... subfolders)
    {
        NasMsg result = newErrorNm();
        if (sayNoToEmptyStrings (folder)) {
            NasMsg nm = new NasMsg (NM_OPCODE_LIST, Paths.get(folder, subfolders).toString(), OUTBOUND);

            if (manipulator.startListRequest (nm))
            try {
                result = (NasMsg) NM_OPCODE_LIST.getSynque().take(); //< ждём ответ манипулятора.
            }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
        return result;
    }

    @Override public @NotNull NasMsg create (String newfoldername)
    {
        NasMsg result = newErrorNm();
        if (sayNoToEmptyStrings (newfoldername)) {
            NasMsg nm = new NasMsg (NM_OPCODE_CREATE, newfoldername, OUTBOUND);

            if (manipulator.startSimpleRequest(nm))
            try {
                result = (NasMsg) NM_OPCODE_CREATE.getSynque().take(); //< ждём ответ манипулятора.
            }
            catch (InterruptedException e) {e.printStackTrace();}
        }
        return result;
    }

    @Override public @NotNull NasMsg rename (FileInfo old, String newName)
    {
        NasMsg result = newErrorNm();
        if (sayNoToEmptyStrings (newName) && old != null) {
            NasMsg nm = new NasMsg (NM_OPCODE_RENAME, newName, OUTBOUND);
            nm.setfileInfo(old);

            if (manipulator.startSimpleRequest(nm))
            try {
                result = (NasMsg) NM_OPCODE_RENAME.getSynque().take(); //< ждём ответ манипулятора.
            }
            catch (InterruptedException e) {e.printStackTrace();}
        }
        return result;
    }

    @Override public @NotNull NasMsg upload (String strLocalFolder,
                                             String strServerFolder,
                                             FileInfo fileInfo)
    {
        NasMsg result = new NasMsg (NM_OPCODE_LOAD2SERVER, strServerFolder, OUTBOUND);
        result.setfileInfo (fileInfo);

        if (fileInfo != null
        && sayNoToEmptyStrings (strLocalFolder, fileInfo.getFileName())
        && !fileInfo.isDirectory())
        {
            //synchronized (netclient2manipulatorMonitor) {
                if (manipulator.startLoad2ServerRequest (strLocalFolder, result)) //{
                    //nmSyncResult = null;
                    try {
                        //while (nmSyncResult == null) netclient2manipulatorMonitor.wait();
                        result = /*nmSyncResult*/(NasMsg) NM_OPCODE_LOAD2SERVER.getSynque().take();
                        //nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                //}
            //}//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg download (String strLocalFolder,
                                               String strServerFolder,
                                               FileInfo fileInfo)
    {
        NasMsg result = new NasMsg (NM_OPCODE_LOAD2LOCAL, strServerFolder, OUTBOUND);
        result.setfileInfo (fileInfo);

        if (fileInfo != null
        && sayNoToEmptyStrings (strLocalFolder, fileInfo.getFileName())
        && !fileInfo.isDirectory())
        {
            //synchronized (netclient2manipulatorMonitor) {
                if (manipulator.startLoad2LocalRequest (strLocalFolder, result))/* {
                    nmSyncResult = null;*/
                    try {
                        //while (nmSyncResult == null) netclient2manipulatorMonitor.wait();
                        result = /*nmSyncResult*/(NasMsg) NM_OPCODE_LOAD2LOCAL.getSynque().take();
                        //nmSyncResult = null;
                    }
                    catch (InterruptedException e) {e.printStackTrace();}
                //}
            //}//sync
        }
        return result;
    }

/** Запрос информации о файле, например, перед его скачиванием. */
    @Override public FileInfo fileInfo (String folder, String fileName)
    {
        FileInfo result = null;
        if (sayNoToEmptyStrings (folder, fileName)) {

            NasMsg nm = new NasMsg (NM_OPCODE_FILEINFO, folder, OUTBOUND);
            nm.setfileInfo(new FileInfo(fileName, NOT_FOLDER, EXISTS));

            if (manipulator.startSimpleRequest(nm))
            try {
                result = ((NasMsg) NM_OPCODE_FILEINFO.getSynque().take()).fileInfo(); //< ждём ответ манипулятора.
            }
            catch (InterruptedException e) {e.printStackTrace();}
        }
        return result;
    }

//посчёт элементов указанного удалённого каталога. -1 означает ошибку.
    @Override public long countFolderEntries (String strParent, final FileInfo fi)
    {
        long result = -1;
        if (sayNoToEmptyStrings(strParent)) {
            NasMsg nm = new NasMsg (NM_OPCODE_COUNTITEMS, strParent, OUTBOUND);
            nm.setfileInfo(fi);

            if (manipulator.startSimpleRequest (nm))
            try {
                nm = (NasMsg) NM_OPCODE_COUNTITEMS.getSynque().take(); //< ждём ответ манипулятора.
            }
            catch (InterruptedException e) {e.printStackTrace();}

            if (nm.opCode() != NM_OPCODE_ERROR)
                result = nm.fileInfo().getFilesize(); //< результат передаётся в поле FileInfo.filesize.
        }
        return result;
    }

    @Override public @NotNull NasMsg delete (String strParent, final FileInfo fi)
    {
        NasMsg result = newErrorNm();
        if (sayNoToEmptyStrings(strParent)) {
            NasMsg nm = new NasMsg (NM_OPCODE_DELETE, strParent, OUTBOUND);
            nm.setfileInfo (fi);

            if (manipulator.startSimpleRequest (nm))
            try {
                result = (NasMsg) NM_OPCODE_DELETE.getSynque().take(); //< ждём ответ манипулятора.
            }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
        return result;
    }

    @Override public @NotNull NasMsg goTo (String folder, String... subfolders)
    {
        return list (folder, subfolders);
    }

/** Отправляем серверу сообщене EXIT  */
    void sendExitMessageToServer ()
    {
        if (schannel != null && schannel.isOpen())
            manipulator.startExitRequest();
    }

    public static NasMsg newErrorNm () {
        return new NasMsg (NM_OPCODE_ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
    }
}
