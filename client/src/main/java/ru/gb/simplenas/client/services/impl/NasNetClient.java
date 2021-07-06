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
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.client.CFactory;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.services.Manipulator;
import ru.gb.simplenas.common.services.impl.NasMsgInboundHandler;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

import java.nio.file.Paths;

import static ru.gb.simplenas.client.Controller.messageBox;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class NasNetClient implements NetClient
{
    private SocketChannel schannel;
    private Channel channelOfChannelFuture;
    private Thread threadNetWork;
    private String userName;
    private Manipulator manipulator;
    private final Thread javafx;
    private final NasCallback callbackOnDisconnection;
    private boolean disconnected = false;

    private final Object syncObj = new Object();
    private final Object syncObj4ConnectionOnly = new Object();
    private NasMsg nmSyncResult;    //< для передачи сообщений между синхронизированными потоками
    private static final Logger LOGGER = LogManager.getLogger(NasNetClient.class.getName());


    public NasNetClient (NasCallback cbDisconnection)
    {
        callbackOnDisconnection = cbDisconnection;
        javafx = Thread.currentThread();
        LOGGER.debug("создан NasNetClient");
    }

//----------------------- колбэки для связи с манипулятором -----------------------------------------------------*/

// Обработка окончания установления соединения : теперь мы можем воспользоваться сохранённой переменной
// типа SocketChannel и продолжить авторизацию в методе NasNetClient.login().
    void callbackOnChannelActive (Object ... objects)     //+l
    {
        synchronized (syncObj)
        {
            LOGGER.debug("callbackOnChannelActive(): *********** соединение установлено");
            syncObj.notifyAll();
        }
    }

//Вызывается из манипулятора. Сейчас юзер и поток javafx ждут окончания операции, которую они нам поручили, и
// результат этой операции они получат в nmSyncResult.
    void callbackOnMsgIncoming (Object ... objects)     //+l
    {
        synchronized (syncObj)
        {
            nmSyncResult = (NasMsg) objects[0];
            LOGGER.trace(">>>> callbackOnMsgIncoming");
            syncObj.notify();//All
        }
    }

//Вызывается из манипулятора. Служит для информирования юзером о событиях.
    void callbackInfo (Object ... objects)     //+l
    {
        NasMsg nm = (NasMsg) objects[0];
        if (nm != null && nm.opCode() == EXIT)
        {
            LOGGER.trace(">>>> callbackInfo");
            Platform.runLater(()->{
                messageBox(CFactory.ALERTHEADER_CONNECTION, PROMPT_CONNECTION_GETTING_CLOSED, Alert.AlertType.WARNING);
            });
        }
    }

 //------------------------------- подключение, отключение, … ----------------------------------------------------*/

// Запускаем поток, который соединяется с сервером.
    @Override public boolean connect()      //+l
    {
        LOGGER.debug("connect() start");
        boolean isOnAir = schannel != null && schannel.isOpen();
        disconnected = false;

        if (isOnAir)
        {   messageBox(CFactory.ALERTHEADER_CONNECTION, "Уже установлено.", Alert.AlertType.INFORMATION);
        }
        else
        {
            LOGGER.debug("connect() запускаем run-поток и 10 сек. ждём syncObj4ConnectionOnly");
            synchronized (syncObj4ConnectionOnly)
            {
                schannel = null;
                threadNetWork = new Thread (this);
                threadNetWork.start();
                try
                {   syncObj4ConnectionOnly.wait(10000);
                }
                catch (InterruptedException e){e.printStackTrace();}
                LOGGER.debug("connect() дождались syncObj4ConnectionOnly");
                isOnAir = schannel != null;
            }//sync
        }
        LOGGER.debug("connect() end");
        return isOnAir;
    }

//run-метод потока, в котором будет работать NasNetClient.
    @Override public void run()     //+l
    {
        LOGGER.debug("run() start");
        NasCallback callbackChannelActive = this::callbackOnChannelActive;
        NasCallback callbackMsgIncoming = this::callbackOnMsgIncoming;
        NasCallback callbackInfo = this::callbackInfo;

        EventLoopGroup groupWorker = new NioEventLoopGroup();
        try
        {   Bootstrap b = new Bootstrap();
            b.group (groupWorker)
             .channel (NioSocketChannel.class)
             .handler (new ChannelInitializer<SocketChannel>()
            {
                @Override protected void initChannel (SocketChannel socketChannel) throws Exception
                {
                    schannel = socketChannel;
                    manipulator = new ClientManipulator(callbackChannelActive,
                                                        callbackMsgIncoming,
                                                        callbackInfo,
                                                        socketChannel);
                    socketChannel.pipeline().addLast  //new StringDecoder(), new StringEncoder(),
                     (      //An encoder which serializes a Java object into a ByteBuf.
                            new ObjectEncoder(),
                            //A decoder which deserializes the received ByteBufs into Java objects.
                            new ObjectDecoder (ClassResolvers.cacheDisabled (null)),
                            new NasMsgInboundHandler (manipulator)
                     );
                    LOGGER.debug("initChannel(): schannel = "+ schannel);
                }
            });
    // !!! Важное замечание: если подключиться к серверу в потоке JavaFX, то
    //          блокировка cfuture.channel().closeFuture().sync()  заблокирует весь клиент.

    //Одинаковыми оказались:
    //      SocketChannel sC,
    //      cfuture.channel(),
    //      ClientManipulator.channelActive().ctx.channel
            LOGGER.debug("run() вызывается  b.connect (SERVER_ADDRESS, PORT).sync()");
            ChannelFuture cfuture = b.connect (SERVER_ADDRESS, PORT).sync();
            channelOfChannelFuture = cfuture.channel();

            LOGGER.debug("run() в try освобождаем  syncObj4ConnectionOnly");
            synchronized (syncObj4ConnectionOnly)
            {   // это продолжит исполнение NasNetClient.connect(), если соединение установлено.
                syncObj4ConnectionOnly.notify();
            }
            cfuture.channel().closeFuture().sync(); //< чтобы перешагнуть через эту строчку, нужно сделать
        }                                           //  socketChannel.close().
        catch (Exception e)
        {
            LOGGER.error("run() : connection interrupted abnormally");
            e.printStackTrace();
        }
        finally
        {
            groupWorker.shutdownGracefully();
            disconnect();
            LOGGER.debug("run() в finally освобождаем  syncObj4ConnectionOnly");
            synchronized (syncObj4ConnectionOnly)
            {   // это продолжит исполнение NasNetClient.connect(), если сервера нет.
                syncObj4ConnectionOnly.notify();
            }
            LOGGER.debug("run(): end");
        }
    }

//Метод может вызываться из Controller.closeSession() и из блока catch в run().
//(закрытие socketChannel приведёт к выполнению блока finally в run().)
    @Override public void disconnect()      //+l
    {
        LOGGER.trace("disconnect() start");
    //предотвращаем повторный вызов (если вызывать этот метод дважды, то дважды появится сообщение о разрыве
    // соединения, что не критично, но выглядит странно)
        if (disconnected) return;
        disconnected = true;

    //делаем что-то в контроллере
        if (callbackOnDisconnection != null)
        {
            callbackOnDisconnection.callback();
        }
    //закрываем канал
        if (schannel != null && schannel.isOpen())
        {
            sendExitMessage();
            schannel.disconnect();
            schannel.close();
            LOGGER.trace("disconnect() закрывается schannel: "+schannel);
        }
        else LOGGER.trace("disconnect() schannel уже закрыт");

        if (channelOfChannelFuture != null && channelOfChannelFuture.isOpen())
        {
            //закрытие желательно, иначе закрытие канала происходит заметно дольше.
            ChannelFuture cf = channelOfChannelFuture.closeFuture();
            LOGGER.trace("disconnect() закрывается channelOfChannelFuture: "+cf);
        }
        else LOGGER.trace("disconnect() channelOfChannelFuture уже закрыт");

        schannel = null;
        channelOfChannelFuture = null;
        threadNetWork = null;
        manipulator = null;
        userName = null;
        LOGGER.trace("disconnect() end");
    }

 //------------------------------- команды для запросов к серверу ------------------------------------------------*/

    @Override public NasMsg login (@NotNull String username)        //+l
    {
        LOGGER.trace("login(): start");
        NasMsg result = null;
        if (sayNoToEmptyStrings(username))
        {
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest (new NasMsg (OperationCodes.LOGIN, username, OUTBOUND)))
                {
                    try {   while (nmSyncResult == null)
                            {
                                syncObj.wait();
                            }
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
            if (result == null)
            {
                result = new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, INBOUND);
            }
            else if (result.opCode() == OperationCodes.OK)
            {
                this.userName = result.msg();
            }
        }
        LOGGER.trace("login(): end");
        return result;
    }

    @Override public @NotNull NasMsg list (@NotNull String folder, String ... subfolders)   //+l
    {
        LOGGER.trace("list(): start");
        NasMsg result = null;
        if (folder != null)
        {
            result = new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg (LIST, Paths.get (folder, subfolders).toString(), OUTBOUND);
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startListRequest(nm))
                {
                    try {   while (nmSyncResult == null)
                            {
                                syncObj.wait();
                            }
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        LOGGER.trace("list(): end");
        return result;
    }

    @Override public @NotNull NasMsg create (@NotNull String newfoldername)     //+l
    {
        LOGGER.trace("create() start");
        NasMsg result = null;
        if (sayNoToEmptyStrings (newfoldername))
        {
            result = new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg(OperationCodes.CREATE, newfoldername, OUTBOUND);
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest (nm))
                {
                    try {   while (nmSyncResult == null)
                            {
                                syncObj.wait();
                            }
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        LOGGER.trace("\t\tNasNetClient.create() end.");
        return result;
    }

    @Override public @NotNull NasMsg rename (@NotNull FileInfo old, @NotNull String newName)    //+l
    {
        LOGGER.trace("rename() start");
        NasMsg result = null;
        if (sayNoToEmptyStrings (newName) && old != null)
        {
            result = new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm =     new NasMsg (OperationCodes.RENAME, newName, OUTBOUND);
                   nm.setfileInfo (old);
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm))
                {
                    try {   while (nmSyncResult == null)
                            {
                                syncObj.wait();
                            }
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        LOGGER.trace("rename() end");
        return result;
    }

    @Override public @NotNull NasMsg transferFile (@NotNull String strLocal, @NotNull String strRemote,
                                                   @NotNull FileInfo fileInfo, OperationCodes opcode)    //+l
    {
        LOGGER.trace("transferFile() start");
        NasMsg result = null;
        if (fileInfo != null && sayNoToEmptyStrings (strLocal, fileInfo.getFileName()) && !fileInfo.isDirectory())
        {
            result = new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm =     new NasMsg (opcode, strRemote, OUTBOUND);
            nm.setfileInfo (fileInfo);
            synchronized (syncObj)
            {
                boolean ok = false;
                if (opcode == LOAD2LOCAL)
                {
                    ok = manipulator.startLoad2LocalRequest (strLocal, nm);
                }
                else if (opcode == LOAD2SERVER)
                {
                    ok = manipulator.startLoad2ServerRequest (strLocal, nm);
                }
                if (ok)
                {
                    nmSyncResult = null;
                    try
                    {   while (nmSyncResult == null)
                        {
                            syncObj.wait();
                        }
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        LOGGER.trace("transferFile() end");
        return result;
    }

    @Override public FileInfo fileInfo (@NotNull String folder, @NotNull String fileName)   //+l
    {
        FileInfo result = null;
        LOGGER.trace("fileInfo() start");
        if (sayNoToEmptyStrings (folder, fileName))
        {
            NasMsg nm = new NasMsg (FILEINFO, folder, OUTBOUND);
            nm.setfileInfo (new FileInfo (fileName, NOT_FOLDER, EXISTS));
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest (nm))
                {
                    try {   while (nmSyncResult == null)
                            {
                                syncObj.wait();
                            }
                            result = nmSyncResult.fileInfo();
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        LOGGER.trace("fileInfo() end");
        return  result;
    }

    //посчёт элементов указанного удалённого каталога. -1 означает ошибку.
    @Override public int countFolderEntries (String strParent, final FileInfo fi)       //+l
    {
        LOGGER.trace("countFolderEntries() start");
        int result = -1;
        if (sayNoToEmptyStrings (strParent))
        {
            NasMsg nm = new NasMsg (OperationCodes.COUNTITEMS, strParent, OUTBOUND);
            nm.setfileInfo (fi);
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest (nm))
                {
                    try {   while (nmSyncResult == null)
                            {
                                syncObj.wait();
                            }
                            nm = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
            if (nm.opCode() != ERROR)
            {
                result = (int)nm.fileInfo().getFilesize();
            }
        }
        LOGGER.trace("countFolderEntries() end");
        return  result;
    }

    @Override public @NotNull NasMsg delete (String strParent, final FileInfo fi)   //+l
    {
        LOGGER.trace("delete() start");
        NasMsg result = null;
        if (sayNoToEmptyStrings (strParent))
        {
            result = new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm =     new NasMsg (OperationCodes.DELETE, strParent, OUTBOUND);
            nm.setfileInfo (fi);
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest(nm))
                {
                    try {   while (nmSyncResult == null)
                            {
                                syncObj.wait();
                            }
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        LOGGER.trace("delete() end");
        return  result;
    }

    @Override public @NotNull NasMsg goTo (@NotNull String folder, String ... subfolders)   //+
    {
        if (folder != null)
        {
            return list (folder, subfolders);
        }
        return new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
    }

    @Override public NasMsg levelUp (@NotNull String strFolder)     //+
    {
        NasMsg result = null;
        if (strFolder != null)
        {
            String strParent = safeRelativeLevelUpStringFrom (userName, strFolder);
            if (sayNoToEmptyStrings(strParent))
            {
                result = list (strParent);
            }
        }
        return result;
    }

//Отправляем серверу сообщене EXIT
    void sendExitMessage()      //+
    {
        if (schannel != null && schannel.isOpen())
        {
            manipulator.startExitRequest (null);
        }
    }

}
//---------------------------------------------------------------------------------------------------------------*/
