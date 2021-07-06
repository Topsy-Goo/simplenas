package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.annotations.EndupMethod;
import ru.gb.simplenas.common.annotations.ManipulateMethod;
import ru.gb.simplenas.common.NasCallback;
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
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class ClientManipulator implements Manipulator
{
    private final SocketChannel schannel;
    private NasDialogue dialogue;
    protected NasCallback callbackChannelActive = this::callbackDummy;  //< для последовательного процесса подключения
    protected NasCallback callbackMsgIncoming = this::callbackDummy;    //< для доставки результатов запросов
    protected NasCallback callbackInfo = this::callbackDummy;           //< для сообщений, которых никто не ждёт

    static final String ERROR_OLD_DIALOGUE_STILL_RUNNING = "Cannot start new dialogue - the previous one slill in use.";
    private static final Logger LOGGER = LogManager.getLogger(ClientManipulator.class.getName());


    private Map<OperationCodes, Method> mapManipulateMetods, mapEndupMetods;


    public ClientManipulator (NasCallback callbackChannelActive,
                              NasCallback callbackMsgIncoming,
                              NasCallback callbackInfo,
                              SocketChannel sC)
    {
        this.callbackChannelActive = callbackChannelActive;
        this.callbackMsgIncoming = callbackMsgIncoming;
        this.callbackInfo = callbackInfo;
        this.schannel = sC;
        new Thread(this::buildMethodMaps).start(); // javafx must die !!!
        LOGGER.debug("создан ClientManipulator");
    }

    void callbackDummy (Object ... objects){}

 //---------------------------------------------------------------------------------------------------------------*/

    @Override public void handle (ChannelHandlerContext ctx, NasMsg nm)
    {
        LOGGER.trace("\nhandle(): start ("+nm+")");
        if (!DEBUG || nm.inbound() == OUTBOUND)
        {
            if (nm != null)
            {
                nm.setinbound (INBOUND);
                try {
                    Method m = mapManipulateMetods.get(nm.opCode());
                    m.invoke(this, nm);
                    }
                catch(IllegalArgumentException | ReflectiveOperationException e){e.printStackTrace();}
            }
        }
        LOGGER.trace("handle(): end ("+nm+")\n");
    }


//Обрабатываем сообщения, которые являются заключительными (терминальными): OK, ERROR.
    @ManipulateMethod (opcodes = {OK, ERROR})
    private void manipulateEndups (NasMsg nm)
    {
        LOGGER.trace("manipulateEndups(): start ("+nm+")");
        if (dialogue != null)
        {
            try {
                Method m = mapEndupMetods.get(dialogue.getTheme());
                m.invoke(this, nm);
                }
            catch(IllegalArgumentException | ReflectiveOperationException e){e.printStackTrace();}
        }
        LOGGER.trace("manipulateEndups(): end ("+nm+")");
    }

//---------------------------------- обработчики простых сообщений ----------------------------------------------*/

//Метод для отправки простых запросов: требующих «односложного» ответа сервера и не требующих пересылки других данных
// кроме самого сообщения.
    @Override public boolean startSimpleRequest (NasMsg nm)     //OUT
    {
        LOGGER.trace("startSimpleRequest(): start ("+nm+")");
        boolean result = false;
        if (nm != null && schannel != null && newDialogue(nm))
        {
            LOGGER.trace("startSimpleRequest(): >>>>>>>>"+nm.opCode()+">>>>>>> "+nm);
            schannel.writeAndFlush(nm);
            result = true;
        }
        LOGGER.trace("startSimpleRequest(): end ("+nm+")");
        return result;
    }

//Метод для обработки простых входящих терминальных сообщений: всё что от них требуется — это передать входящее
// сообщение процедуре, которая организовала запрос.
    @EndupMethod (opcodes = {CREATE, RENAME, FILEINFO, COUNTITEMS, DELETE, LOAD2SERVER, LOGIN})
    private void endupSimpleRequest (NasMsg nm)                 //IN
    {
        LOGGER.trace("endupSimpleRequest(): start ("+nm+")");
        if (dialogue != null)
        {
            dialogue.add(nm);
            callbackMsgIncoming.callback(nm);
            stopTalking();
        }
        LOGGER.trace("endupSimpleRequest(): end ("+nm+")");
    }

//---------------------------------- LIST -----------------------------------------------------------------------*/

    @Override public boolean startListRequest (NasMsg nm)               //OUT
    {
        LOGGER.trace("startListRequest(): start ("+nm+")");
        boolean done = false;
        if (nm != null && schannel != null && newDialogue (nm, newInfolist()))
        {
            LOGGER.trace("startListRequest(): >>>>>>>>"+nm.opCode()+">>>>>>> "+ nm);
            schannel.writeAndFlush(nm);
            done = true;
        }
        LOGGER.trace("startListRequest(): end ("+nm+")");
        return done;
    }

    @ManipulateMethod (opcodes = {OperationCodes.LIST})             //IN, IN, ..., IN
    private void manipulateListQueue (NasMsg nm)
    {
        LOGGER.trace("manipulateListQueue(): start ("+nm+")");
        if (dialogue != null)
        {
            //dialogue.add(nm);     TODO : проверить в тесте,что здесь nm не записывается в dialogue
            if (dialogue.infolist() != null)
            {
                dialogue.infolist().add(nm.fileInfo());
                LOGGER.trace(nm.fileInfo().isDirectory() ? "D":"f");
            }
        }
        LOGGER.trace("manipulateListQueue(): end ("+nm+")");
    }

// обработка завершения передачи списка содержимого удалённой папки
    @EndupMethod (opcodes = {LIST})
    private void endupListRequest (NasMsg nm)                           //IN
    {
        LOGGER.trace("endupListRequest(): start ("+nm+")");
        if (dialogue != null)
        {
            dialogue.add(nm);
            nm.setdata (dialogue.infolist()); //< список, который кропотливо составлял manipulateInboundList().

            if (nm.data() == null)
            {
                if (DEBUG)  throw new RuntimeException();
                nm.setdata (newInfolist()); //< чтобы не мучиться с проверками
            }
            callbackMsgIncoming.callback(nm);
            stopTalking();
        }
        LOGGER.trace("endupListRequest(): end ("+nm+")");
    }

//---------------------------------- LOAD2LOCAL -----------------------------------------------------------------*/

    @Override public boolean startLoad2LocalRequest (String toLocalFolder, NasMsg nm)       //OUT
    {
        LOGGER.trace("startLoad2LocalRequest(): start ("+nm+")");
        boolean result = false;
        if (nm != null && schannel != null)
        {
            if (newDialogue(nm, new ClientInboundFileExtruder(), toLocalFolder))
            {
                LOGGER.trace("startLoad2LocalRequest(): >>>>>>>>"+nm.opCode()+">>>>>>> "+nm);
                schannel.writeAndFlush(nm);
                result = true;
            }
            if (!result)
            {
                nm.setOpCode(ERROR);
                stopTalking();
            }
        }
        LOGGER.trace("startLoad2LocalRequest(): end ("+nm+")");
        return result;
    }

    @ManipulateMethod (opcodes = {OperationCodes.LOAD2LOCAL})
    private void manipulateLoad2LocalQueue (NasMsg nm)                                      //IN, IN, ..., IN
    {
        LOGGER.trace("manipulateLoad2LocalQueue(): start ("+nm+")");
        if (dialogue != null)
        {
            //dialogue.add(nm);  TODO : проверить в тесте,что здесь nm не записывается в dialogue
            dialogue.dataBytes2File(nm);
            //полyчаем кусочки файла от сервера и записываем их в файл; если в процессе возникнут
            // ошибки на нашей стороне, передачу не прерываем, а просто ждём её окончания.
        }
        LOGGER.trace("manipulateLoad2LocalQueue(): end ("+nm+")");
    }

    @EndupMethod (opcodes = {LOAD2LOCAL})
    private void endupLoad2LocalRequest (NasMsg nm)                                         //IN
    {
        LOGGER.trace("endupLoad2LocalRequest(): start ("+nm+")");
        if (dialogue != null)
        {
            dialogue.add(nm);

            if (DEBUG)
            {
                FileInfo fi = nm.fileInfo();
                if (nm.opCode() == OK)
                    LOGGER.debug("ОТДАЧА файла завершена «"+fi.getFileName()+"»: размер="+fi.getFilesize()+", chuncks="+dialogue.getChunks()+"");
                else
                    LOGGER.debug("ОТДАЧА файла завершилась ошибкой");
            }
        //переносим файл из временной папки в папку назначения
            if (nm.opCode() == OK  &&  !dialogue.endupExtruding(nm))
            {
                nm.setOpCode(ERROR);
            }
            callbackMsgIncoming.callback(nm);
            stopTalking();
        }
        LOGGER.trace("endupLoad2LocalRequest(): end ("+nm+")");
    }

//---------------------------------- LOAD2SERVER ----------------------------------------------------------------*/

//TODO : иногда файл не копируется на сервер с первого раза или не копируется никогда. Это, судя по всему,
//       связано с предоставлением прав на доступ к файлу. Эти трудные файлы, как праило, служебные.

    @Override public boolean startLoad2ServerRequest (String fromLocalFolder, NasMsg nm)    //OUT
    {
        LOGGER.trace("startLoad2ServerRequest(): start ("+nm+")");
        boolean result = false;
        if (nm != null && nm.fileInfo() != null && schannel != null)
        {
            InputStream is = inputstreamByFilename (fromLocalFolder, nm.fileInfo().getFileName());
            if (is != null && newDialogue (nm, is))
            {
                LOGGER.trace("startLoad2ServerRequest(): >>>>>>>>"+nm.opCode()+">>>>>>> "+nm);
                schannel.writeAndFlush(nm);
                result = dialogue.inputStream() != null;
            }
            if (!result)
            {
                nm.setOpCode(ERROR);
                stopTalking();
            }
        }
        LOGGER.trace("startLoad2ServerRequest(): end ("+nm+")");
        return result;
    }

    @ManipulateMethod (opcodes = {OperationCodes.LOAD2SERVER})
    private void manipulateLoad2ServerQueue (NasMsg nm)                                     //IN, OUT, OUT, ..., OUT
    {
        LOGGER.trace("manipulateLoad2ServerQueue(): start ("+nm+")");
        if (dialogue != null)
        {
            dialogue.add(nm);
            boolean result = false;
            try
            {
                if (dialogue.inputStream() != null  &&  sendFileToServer (nm))
                {
                    informOnSuccessfulDataTrabsfer (nm);
                    result = true;
                }
            }
            catch (IOException e) {e.printStackTrace();}
            finally
            {
                if (!result)    replyWithErrorMessage();    //< Сервер ответит в любом случае, поэтому очистку здесь не делаем
            }
        }
        LOGGER.trace("manipulateLoad2ServerQueue(): end ("+nm+")");
    }

    private boolean sendFileToServer (NasMsg nm) throws IOException
    {
        if (nm.fileInfo() == null || dialogue == null || schannel == null)
        {
            return false;
        }
        final long size = nm.fileInfo().getFilesize();
        long read = 0L;
        long rest = size;
        byte[] array = new byte[MAX_BUFFER_SIZE];
        InputStream istream = dialogue.inputStream();

        nm.setinbound (OUTBOUND);
        nm.setdata (array);

        LOGGER.trace("sendFileToServer(): начало пересылки данных>>>>>>>");
        while (rest > 0 && istream != null)
        {
            read = istream.read (array, 0, MAX_BUFFER_SIZE); //< блокирующая операция; вернёт -1, если достугнут конец файла
            if (read <= 0)
            {
                break;
            }
            rest -= read;
            nm.fileInfo().setFilesize (read);   //< пусть nm.fileInfo.filesize содержит количество считанных байтов
            schannel.writeAndFlush (nm);
            dialogue.incChunks();
        }
        LOGGER.trace("sendFileToServer(): >>>>>>>>конец пересылки данных");

        nm.fileInfo().setFilesize (size);
        return rest == 0L;
    }

    private void informOnSuccessfulDataTrabsfer (NasMsg nm)
    {
        if (dialogue != null && schannel != null)
        {
            nm.setOpCode(OK);
            nm.setinbound (OUTBOUND);
            nm.setdata (null);
            dialogue.add(nm);
            LOGGER.trace(" >>>>>>>>"+nm.opCode()+">>>>>>> "+nm);
            schannel.writeAndFlush (nm);
        }
    }

 //--------------------------------- EXIT ------------------------------------------------------------------------*/

//NetClient прислал задачу отправить серверу сообщение EXIT. Завершаем текущую операцию и отсылаем EXIT серверу.
    @Override public void startExitRequest (NasMsg nm)      //OUT
    {
        LOGGER.trace("startExitRequest(): start ("+nm+")");
        discardCurrentOperation (nm);  //< скорее всего это не понадобиться, т.к. GUI блокируется на время операции
        if (nm == null && schannel != null)
        {
            nm = new NasMsg (EXIT, OUTBOUND);
            LOGGER.trace("startExitRequest(): >>>>>>>>"+nm.opCode()+">>>>>>> "+nm);
            schannel.writeAndFlush (nm);
        }
        LOGGER.trace("startExitRequest(): end ("+nm+")");
    }

//прерываем текущую операцию. некоторым операциям может потребоваться особый способ прерывания.
    private void discardCurrentOperation (NasMsg nm)
    {
        LOGGER.trace("discardCurrentOperation(): start");
        if (dialogue != null)
        {
            if (dialogue.getTheme() == LOAD2LOCAL)
            {
                dialogue.discardExtruding();
            }
    /*  если от сервера EXIT пришёл во время выполнения запроса юзера, то opCode = ERROR заставит вызывающий метод
        стандартно обработать не(до)получение данных, а сообщение в nm.msg о разрыве соединения всё объяснит юзеру. */
            nm.setOpCode(ERROR);
            callbackMsgIncoming.callback(nm);
            stopTalking();
        }
        LOGGER.trace("discardCurrentOperation(): end");
    }

//сервер прислал EXIT. Прерываем текущую задачу и сообщаем юзеру об обрыве связи.
    @ManipulateMethod(opcodes = {EXIT})
    private void manipulateExitRequest (NasMsg nm)          //IN
    {
        LOGGER.trace("manipulateExitRequest(): start ("+nm+")");
        discardCurrentOperation (nm);
        callbackInfo.callback(nm);
        LOGGER.trace("manipulateExitRequest(): end ("+nm+")");
    }

//---------------------------------- общение с InboundHandler'ом ------------------------------------------------*/

    //Возможно, эти три метода нужно будет похерить, но это будет видно позже; сейчас они нужны серверу для
    @Override public void onChannelActive (ChannelHandlerContext ctx)
    {
        LOGGER.info("onChannelActive(): открыто соединение: cts: "+ ctx.channel());
        callbackChannelActive.callback();   //< этого колбэка ждёт NetClient.login(), чтобы продолжить работу.
    }
    @Override public void onChannelInactive (ChannelHandlerContext ctx)
    {
        LOGGER.info("onChannelInactive(): закрыто соединение: ctx: "+ ctx);
    }
    @Override public void onExceptionCaught (ChannelHandlerContext ctx, Throwable cause)
    {
        LOGGER.error("onExceptionCaught(): аварийное закрытие соединения: ctx: "+ ctx);
    }

//---------------------------------- другие полезные методы -----------------------------------------------------*/

//методы для создания dialogue.
    private boolean newDialogue (@NotNull NasMsg nm)
    {
        LOGGER.trace("newDialogue(nm): start ("+nm+")("+dialogue+")");
        if (dialogue != null)
        {
            //throw new RuntimeException();
            LOGGER.error("newDialogue(): "+ ERROR_OLD_DIALOGUE_STILL_RUNNING);
            LOGGER.trace("newDialogue(nm): end ("+nm+")");
            return false;
        }
        dialogue = new NasDialogue(nm);
        LOGGER.trace("newDialogue(nm): end ("+nm+")");
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull List<FileInfo> infolist)
    {
        LOGGER.trace("newDialogue(il): start ("+nm+")("+dialogue+")");
        if (dialogue != null)
        {
            LOGGER.error("newDialogue(): "+ ERROR_OLD_DIALOGUE_STILL_RUNNING);
            LOGGER.trace("newDialogue(il): end ("+nm+")");
            return false;
        }
        if (infolist != null)
        {   dialogue = new NasDialogue(nm, infolist);
        }
        LOGGER.trace("newDialogue(il): end ("+nm+")");
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull InputStream inputStream)
    {
        LOGGER.trace("newDialogue(is): start ("+nm+")("+dialogue+")");
        if (dialogue != null)
        {
            LOGGER.error("newDialogue(): "+ ERROR_OLD_DIALOGUE_STILL_RUNNING);
            LOGGER.trace("newDialogue(is): end ("+nm+")");
            return false;
        }
        if (inputStream != null)
        {   dialogue = new NasDialogue(nm, inputStream);
        }
        LOGGER.trace("newDialogue(is): end ("+nm+")");
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull FileExtruder fe, @NotNull String toLocalFolder)       //+l
    {
        LOGGER.trace("newDialogue(fe): start ("+nm+")("+dialogue+")");
        boolean ok = false;
        if (dialogue != null)
        {
            LOGGER.error("newDialogue(): "+ ERROR_OLD_DIALOGUE_STILL_RUNNING);
            LOGGER.trace("newDialogue(fe): end ("+nm+")");
            ok = false;
        }
        else if (fe != null && sayNoToEmptyStrings (toLocalFolder))
        {
            dialogue = new NasDialogue(nm, fe);
            ok = dialogue.initializeFileExtruder(nm, toLocalFolder);
        }
        LOGGER.trace("newDialogue(fe): end ("+nm+")");
        return ok;
    }

    private void stopTalking()      //+l
    {
        LOGGER.trace("\t\t\t\tstopTalking() call");
        if (dialogue != null)
        {
            dialogue.close();
            dialogue = null;
        }
    }

//Шлём серверу сообщение об ошибке.
    public void replyWithErrorMessage()     //+l
    {
        LOGGER.trace("replyWithErrorMessage(): start");
        if (dialogue != null && schannel != null)
        {
            NasMsg nm = new NasMsg (ERROR, STR_EMPTY, OUTBOUND);
            dialogue.add(nm);

            LOGGER.trace("replyWithErrorMessage(): >>>>>>>>"+nm.opCode()+">>>>>>> "+nm);
            schannel.writeAndFlush (nm);
        }
        LOGGER.trace("replyWithErrorMessage(): end");
    }

 //Составление списков методов, чтобы использовать эти спики вместо switch-case'ов.
    private void buildMethodMaps()      //+l
    {
        LOGGER.trace("buildMethodMaps(): start");
        mapManipulateMetods = new HashMap<>();
        mapEndupMetods = new HashMap<>();
        Method[] methods = ClientManipulator.class.getDeclaredMethods ();

        for (Method m : methods)
        {
            if (m.isAnnotationPresent(ManipulateMethod.class))
            {
                ManipulateMethod annotation = m.getAnnotation (ManipulateMethod.class);
                OperationCodes[] opcodes = annotation.opcodes();
                for (OperationCodes code : opcodes)
                {
                    mapManipulateMetods.put(code, m);
                }
            }
            if (m.isAnnotationPresent(EndupMethod.class))
            {
                EndupMethod annotation = m.getAnnotation (EndupMethod.class);
                OperationCodes[] opcodes = annotation.opcodes();
                for (OperationCodes code : opcodes)
                {
                    mapEndupMetods.put(code, m);
                }
            }
        }
        LOGGER.trace("buildMethodMaps(): end");
    }

    public static InputStream inputstreamByFilename (String strFolder, String strFileName)      //+
    {
        InputStream inputstream = null;
        if (sayNoToEmptyStrings(strFolder, strFileName))
        {
            Path pLocalFile = Paths.get(strFolder, strFileName).toAbsolutePath().normalize();
            if (!Files.isDirectory(pLocalFile) && Files.exists(pLocalFile))
            {
                try {   inputstream = Files.newInputStream(pLocalFile, READ);
                    }
                catch (IOException e){e.printStackTrace();}
            }
        }
        return inputstream;

        //TODO : для некоторых файлов система не даёт создать InputStream. Должно быть, дело в правах
        //       доступа, т.к. каждый раз эти файлы выглядят служебными. Иногда попытка удаётся, но
        //       не с первого раза, что подтверждает догадку.
    }

}// class ClientManipulator
 //---------------------------------------------------------------------------------------------------------------*/

