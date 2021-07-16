package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
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

public class LocalNetClient implements NetClient
{
    private SocketChannel schannel;
    private Channel channelOfChannelFuture;
    private Thread threadNetWork;
    private String userName;
    private ClientManipulator manipulator;
    private NasCallback callbackOnDisconnection = this::callbackDummy;
    private boolean connected = false;

    private final Object syncObj = new Object();
    private final Object syncObj4ConnectionOnly = new Object();
    private NasMsg nmSyncResult;    //< для передачи сообщений между синхронизированными потоками
    private NasDialogue closedDialogue;
    private final int port;
    private final String hostName;
    private static final Logger LOGGER = LogManager.getLogger(LocalNetClient.class.getName());


    public LocalNetClient (NasCallback cbDisconnection, int port, String hostName)
    {
        callbackOnDisconnection = cbDisconnection;
        this.port = port;
        this.hostName = hostName;
        LOGGER.debug("создан LocalNetClient");
    }

//----------------------- колбэки для связи с манипулятором -----------------------------------------------------*/

    void callbackDummy (Object ... objects){}

// Обработка окончания установления соединения : теперь мы можем воспользоваться сохранённой переменной
// типа SocketChannel и продолжить авторизацию в методе LocalNetClient.login().
    void callbackOnChannelActive (Object ... objects)
    {
        synchronized (syncObj)
        {
            syncObj.notifyAll();
        }
    }

//Вызывается из манипулятора. Сейчас юзер и поток javafx ждут окончания операции, которую они нам поручили, и
// результат этой операции они получат в nmSyncResult.
    void callbackOnMsgIncoming (Object ... objects)
    {
        synchronized (syncObj)
        {
            if (objects != null)
            {
                int count = objects.length;
                if (count > 0)
                    nmSyncResult = (NasMsg) objects[0];
                if (count > 1)
                    closedDialogue = (NasDialogue) objects[1];
                syncObj.notify();//All
            }
        }
    }

//Вызывается из манипулятора. Служит для информирования юзером о событиях.
    void callbackInfo (Object ... objects)
    {
        NasMsg nm = (NasMsg) objects[0];
        if (nm != null && nm.opCode() == EXIT)
        {
            Platform.runLater(()->{
                messageBox(CFactory.ALERTHEADER_CONNECTION, PROMPT_CONNECTION_GETTING_CLOSED, Alert.AlertType.WARNING);
            });
        }
    }

//------------------------------- подключение, отключение, … ----------------------------------------------------*/

// Запускаем поток, который соединяется с сервером.
    @Override public boolean connect()
    {
        boolean isOnAir = schannel != null && schannel.isOpen();

        if (isOnAir)
            messageBox(CFactory.ALERTHEADER_CONNECTION, "Уже установлено.", Alert.AlertType.INFORMATION);
        else
        {
            synchronized (syncObj4ConnectionOnly)
            {
                schannel = null;
                threadNetWork = new Thread (this);
                //threadNetWork.setDaemon(true);
                threadNetWork.start();
                try
                {   syncObj4ConnectionOnly.wait(10000);
                }
                catch (InterruptedException e){e.printStackTrace();}
                isOnAir = schannel != null;
            }//sync
        }
        return isOnAir;
    }

//run-метод потока, в котором будет работать LocalNetClient.
    @Override public void run()
    {
        NasCallback callbackChannelActive = this::callbackOnChannelActive;
        NasCallback callbackMsgIncoming = this::callbackOnMsgIncoming;
        NasCallback callbackInfo = this::callbackInfo;

        EventLoopGroup groupWorker = new NioEventLoopGroup();
        try
        {   Bootstrap b = new Bootstrap();
            b.group (groupWorker)
             .channel (NioSocketChannel.class)
             //.option (ChannelOption.SO_KEEPALIVE, true)
             .handler (new ChannelInitializer<SocketChannel>()
            {
                @Override protected void initChannel (SocketChannel socketChannel) throws Exception
                {
                    schannel = socketChannel;
                    manipulator = new LocalManipulator(callbackChannelActive,
                                                       callbackMsgIncoming,
                                                       callbackInfo,
                                                       socketChannel);
                    socketChannel.pipeline().addLast
                     (      //new StringDecoder(), new StringEncoder(),
                            //An encoder which serializes a Java object into a ByteBuf.
                            new ObjectEncoder(),
                            //A decoder which deserializes the received ByteBufs into Java objects.
                            //new ObjectDecoder (ClassResolvers.cacheDisabled (null)),
                            //new ObjectDecoder (MAX_OBJECT_SIZE, ClassResolvers.cacheDisabled (null)),
                            new ObjectDecoder (Integer.MAX_VALUE, ClassResolvers.weakCachingConcurrentResolver(null)),
                            new NasMsgInboundHandler (manipulator)
                            //new TestInboundHandler (manipulator)
                     );
                }
            });
    // !!! Важное замечание: если подключиться к серверу в потоке JavaFX, то
    //          блокировка cfuture.channel().closeFuture().sync()  заблокирует весь клиент.

    //Одинаковыми оказались:
    //      SocketChannel sC,
    //      cfuture.channel(),
    //      LocalManipulator.channelActive().ctx.channel

            ChannelFuture cfuture = b.connect (hostName, port).sync();
            this.channelOfChannelFuture = cfuture.channel();
            this.connected = true;

            lnprint("\n\t\t*** Connected ("+port+"). ***\n");

            synchronized (syncObj4ConnectionOnly)
            {   // это продолжит исполнение LocalNetClient.connect(), если соединение установлено.
                syncObj4ConnectionOnly.notify();
            }
            cfuture.channel().closeFuture().sync(); //< чтобы перешагнуть через эту строчку, нужно сделать
        }                                           //  socketChannel.close().
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            groupWorker.shutdownGracefully();
            disconnect();
            synchronized (syncObj4ConnectionOnly)
            {   // это продолжит исполнение LocalNetClient.connect(), если сервера нет.
                syncObj4ConnectionOnly.notify();
            }
        }
    }

//Метод может вызываться из Controller.closeSession() и из блока catch в run().
//(закрытие socketChannel приведёт к выполнению блока finally в run().)
    @Override public void disconnect ()
    {
    //делаем что-то в контроллере
        if (connected) callbackOnDisconnection.callback();

    //закрываем канал
        if (schannel != null && schannel.isOpen())
        {
            sendExitMessage();
            schannel.disconnect();
        }
        if (channelOfChannelFuture != null && channelOfChannelFuture.isOpen())
        {
            //закрытие желательно, иначе закрытие канала происходит заметно дольше.
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

    @Override public NasMsg login (@NotNull String username)
    {
        NasMsg result = null;
        if (sayNoToEmptyStrings(username))
        {
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startSimpleRequest (new NasMsg (OperationCodes.LOGIN, username, OUTBOUND)))
                {
                    try {   while (nmSyncResult == null)
                                syncObj.wait();
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
        return result;
    }

    @Override public @NotNull NasMsg list (@NotNull String folder, String ... subfolders)
    {
        NasMsg result = null;
        if (sayNoToEmptyStrings (folder))
        {
            result = new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
            NasMsg nm = new NasMsg (LIST, Paths.get (folder, subfolders).toString(), OUTBOUND);
            synchronized (syncObj)
            {
                nmSyncResult = null;
                if (manipulator.startListRequest(nm))
                {
                    try {   while (nmSyncResult == null)
                                syncObj.wait();
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg create (@NotNull String newfoldername)
    {
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
                                syncObj.wait();
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg rename (@NotNull FileInfo old, @NotNull String newName)
    {
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
                                syncObj.wait();
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg upload (@NotNull String strLocalFolder, @NotNull String strServerFolder,
                                             @NotNull FileInfo fileInfo)
    {
        NasMsg result = null;
        if (fileInfo != null && sayNoToEmptyStrings (strLocalFolder, fileInfo.getFileName()) && !fileInfo.isDirectory())
        {
            result = new NasMsg (LOAD2SERVER, strServerFolder, OUTBOUND);
            result.setfileInfo (fileInfo);
            synchronized (syncObj)
            {
                if (manipulator.startLoad2ServerRequest (strLocalFolder, result))
                {
                    nmSyncResult = null;
                    try
                    {   while (nmSyncResult == null)
                            syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public @NotNull NasMsg download (@NotNull String strLocalFolder, @NotNull String strServerFolder,
                                               @NotNull FileInfo fileInfo)
    {
        NasMsg result = null;
        if (fileInfo != null && sayNoToEmptyStrings (strLocalFolder, fileInfo.getFileName()) && !fileInfo.isDirectory())
        {
            result = new NasMsg (LOAD2LOCAL, strServerFolder, OUTBOUND);
            result.setfileInfo (fileInfo);
            synchronized (syncObj)
            {
                if (manipulator.startLoad2LocalRequest (strLocalFolder, result))
                {
                    nmSyncResult = null;
                    try
                    {   while (nmSyncResult == null)
                            syncObj.wait();
                        result = nmSyncResult;
                        nmSyncResult = null;
                    }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        return result;
    }

    @Override public FileInfo fileInfo (@NotNull String folder, @NotNull String fileName)
    {
        FileInfo result = null;
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
                                syncObj.wait();
                            result = nmSyncResult.fileInfo();
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        return  result;
    }

    //посчёт элементов указанного удалённого каталога. -1 означает ошибку.
    @Override public int countFolderEntries (String strParent, final FileInfo fi)
    {
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
                                syncObj.wait();
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
        return  result;
    }

    @Override public @NotNull NasMsg delete (String strParent, final FileInfo fi)
    {
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
                                syncObj.wait();
                            result = nmSyncResult;
                            nmSyncResult = null;
                        }
                    catch (InterruptedException e){e.printStackTrace();}
                }
            }//sync
        }
        return  result;
    }

    @Override public @NotNull NasMsg goTo (@NotNull String folder, String ... subfolders)
    {
        if (folder != null)
        {
            return list (folder, subfolders);
        }
        return new NasMsg (ERROR, ERROR_UNABLE_TO_PERFORM, OUTBOUND);
    }

    //@Override public NasMsg levelUp (@NotNull String strFolder)
    //{
    //    NasMsg result = null;
    //    if (strFolder != null)
    //    {
    //        String strParent = getParentFromRelative (userName, strFolder);
    //        if (sayNoToEmptyStrings (strParent))
    //        {
    //            result = goTo (strParent);
    //        }
    //    }
    //    return result;
    //}

//Отправляем серверу сообщене EXIT
    void sendExitMessage()
    {
        if (schannel != null && schannel.isOpen())
        {
            manipulator.startExitRequest (null);
        }
    }

}
//---------------------------------------------------------------------------------------------------------------*/
