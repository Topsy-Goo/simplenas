package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.client.services.ClientManipulator;
import ru.gb.simplenas.common.annotations.EndupMethod;
import ru.gb.simplenas.common.annotations.ManipulateMethod;
import ru.gb.simplenas.common.NasCallback;
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
import static ru.gb.simplenas.client.CFactory.PROMPT_FORMAT_UPLOADERROR_SRCFILE_ACCESS;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class LocalManipulator implements ClientManipulator
{
    private final SocketChannel schannel;
    private NasDialogue dialogue;
    protected NasCallback callbackChannelActive = this::callbackDummy;  //< для последовательного процесса подключения
    protected NasCallback callbackMsgIncoming = this::callbackDummy;    //< для доставки результатов запросов
    protected NasCallback callbackInfo = this::callbackDummy;           //< для сообщений, которых никто не ждёт
    private Map<OperationCodes, Method> mapManipulateMetods, mapEndupMetods;
    private static final String ERROR_OLD_DIALOGUE_STILL_RUNNING = "Не могу начать новый диалог, — предыдущий ещё не закрыт.";
    private static final Logger LOGGER = LogManager.getLogger(LocalManipulator.class.getName());

    public LocalManipulator (NasCallback callbackChannelActive,
                             NasCallback callbackMsgIncoming,
                             NasCallback callbackInfo,
                             SocketChannel sC)
    {
        this.callbackChannelActive = callbackChannelActive;
        this.callbackMsgIncoming = callbackMsgIncoming;
        this.callbackInfo = callbackInfo;
        this.schannel = sC;
        buildMethodsMaps();
        LOGGER.debug("создан LocalManipulator");
    }

    void callbackDummy (Object ... objects){}

 //---------------------------------------------------------------------------------------------------------------*/

    @Override public void handle (ChannelHandlerContext ctx, NasMsg nm)
    {
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
    }


//Обрабатываем сообщения, которые являются заключительными (терминальными): OK, ERROR.
    @ManipulateMethod (opcodes = {OK, ERROR})
    private void manipulateEndups (NasMsg nm)
    {
        if (dialogue != null)
        {
            try {
                Method m = mapEndupMetods.get(dialogue.getTheme());
                m.invoke(this, nm);
                }
            catch(IllegalArgumentException | ReflectiveOperationException e){e.printStackTrace();}
        }
    }

//---------------------------------- обработчики простых сообщений ----------------------------------------------*/

//Метод для отправки простых запросов: требующих «односложного» ответа сервера и не требующих пересылки других данных
// кроме самого сообщения.
    @Override public boolean startSimpleRequest (NasMsg nm)     //OUT
    {
        boolean result = false;
        if (nm != null && schannel != null && newDialogue(nm))
        {
            schannel.writeAndFlush(nm);
            result = true;
        }
        return result;
    }

//Метод для обработки простых входящих терминальных сообщений: всё что от них требуется — это передать входящее
// сообщение процедуре, которая организовала запрос.
    @EndupMethod (opcodes = {CREATE, RENAME, FILEINFO, COUNTITEMS, DELETE, LOAD2SERVER, LOGIN})
    private void endupSimpleRequest (NasMsg nm)                 //IN
    {
        if (dialogue != null)
        {
            lnprint("M:пришло сообщение: "+ nm.opCode()+"; тема: "+dialogue.getTheme());
            dialogue.add(nm);
            stopTalking (nm);
        }
    }

//---------------------------------- LIST -----------------------------------------------------------------------*/

    @Override public boolean startListRequest (NasMsg nm)           //OUT
    {
        boolean done = false;
        if (nm != null && schannel != null && newDialogue (nm, newInfolist()))
        {
            schannel.writeAndFlush(nm);
            lnprint("M:отправлено сообщение: "+ nm.opCode()+"\n");
            done = true;
        }
        return done;
    }

    @ManipulateMethod (opcodes = {LIST})                            //IN, IN, ..., IN
    private void manipulateListQueue (NasMsg nm)
    {
        if (dialogue != null)
        {
            //dialogue.add(nm);     TODO : проверить в тесте,что здесь nm не записывается в dialogue
            print("l");
            if (dialogue.infolist() != null)
            {
                dialogue.infolist().add(nm.fileInfo());
            }
        }
    }

// обработка завершения передачи списка содержимого удалённой папки
    @EndupMethod (opcodes = {LIST})
    private void endupListRequest (NasMsg nm)                       //IN
    {
        if (dialogue != null)
        {
            lnprint("M:пришло сообщение: "+ nm.opCode()+"; тема: "+dialogue.getTheme());
            dialogue.add(nm);
            nm.setdata (dialogue.infolist()); //< список, который кропотливо составлял manipulateInboundList().

            if (nm.data() == null)
                nm.setdata (newInfolist());

            stopTalking (nm);
        }
    }

//---------------------------------- LOAD2LOCAL -----------------------------------------------------------------*/

    @Override public boolean startLoad2LocalRequest (String toLocalFolder, NasMsg nm)       //OUT
    {
        boolean result = false;
        String errMsg = ERROR_UNABLE_TO_PERFORM;

        if (nm != null && schannel != null && nm.fileInfo() != null)
        {
            String fileName = nm.fileInfo().getFileName();
            if (sayNoToEmptyStrings (toLocalFolder, fileName, nm.msg()))
            {
                Path ptargetfile = Paths.get(toLocalFolder, fileName);
                if (newDialogue (nm, new InboundFileExtruder(ptargetfile)))
                {
                    schannel.writeAndFlush(nm);
                    lnprint("M:отправлено сообщение: "+ nm.opCode() +"\n");
                    result = true;
                }
                else errMsg = sformat("%s\n\n%s\n", errMsg, ptargetfile);
            }
        }
        if (!result && nm != null)
        {
            nm.setOpCode(ERROR);
            nm.setmsg(errMsg);
        }
        return result;
    }

    @ManipulateMethod (opcodes = {LOAD2LOCAL})
    private void manipulateLoad2LocalQueue (NasMsg nm)                                      //IN, IN, ..., IN
    {
        if (dialogue != null)
        {
            //dialogue.add(nm);  TODO : проверить в тесте,что здесь nm не записывается в dialogue
            dialogue.writeDataBytes2File(nm);
            //полyчаем кусочки файла от сервера и записываем их в файл; если в процессе возникнут
            // ошибки на нашей стороне, передачу не прерываем, а просто ждём её окончания.
        }
    }

    @EndupMethod (opcodes = {LOAD2LOCAL})
    private void endupLoad2LocalRequest (NasMsg nm)                                         //IN
    {
        lnprint("M:получено сообщение: "+ nm.opCode() +"; тема: "+dialogue.getTheme());
        if (dialogue != null)
        {
            dialogue.add(nm);
            if (nm.opCode() == OK  &&  !dialogue.endupExtruding(nm))
            {
                nm.setOpCode(ERROR);
            }
            stopTalking (nm);
        }
    }

//---------------------------------- LOAD2SERVER ----------------------------------------------------------------*/

    @Override public boolean startLoad2ServerRequest (String fromLocalFolder, NasMsg nm)    //OUT
    {
        boolean result = false;
        if (nm != null && nm.fileInfo() != null && schannel != null)
        {
            InputStream is = inputstreamByFilename (fromLocalFolder, nm.fileInfo().getFileName());
            if (is == null)
            {
                nm.setOpCode(ERROR);
                nm.setmsg(sformat(PROMPT_FORMAT_UPLOADERROR_SRCFILE_ACCESS, fromLocalFolder, strFileSeparator, nm.fileInfo().getFileName()));
            }
            else if (newDialogue (nm, is))
            {
                schannel.writeAndFlush(nm);
                lnprint("M:отправлено сообщение: "+ nm.opCode() +"\n");
                result = true;
            }
        }
        return result;
    }

    public static InputStream inputstreamByFilename (String strFolder, String strFileName)
    {
        InputStream inputstream = null;
        if (sayNoToEmptyStrings(strFolder, strFileName))
        {
            Path pLocalFile = Paths.get(strFolder, strFileName).toAbsolutePath().normalize();
            if (!Files.isDirectory(pLocalFile) && Files.isReadable(pLocalFile))
            {
                try {   inputstream = Files.newInputStream(pLocalFile, READ);
                    }
                catch (IOException e){e.printStackTrace();}
            }
        }
        return inputstream;

        //TODO : для некоторых файлов система не даёт создать InputStream. Должно быть, дело в правах
        //       доступа, т.к. каждый раз эти файлы выглядят служебными. Иногда попытка удаётся, но
        //       не с первого раза.
    }

    @ManipulateMethod (opcodes = {LOAD2SERVER})
    private void manipulateLoad2ServerQueue (NasMsg nm)                                     //IN, OUT, OUT, ..., OUT
    {
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
    }

    private boolean sendFileToServer (NasMsg nm) throws IOException
    {
        InputStream istream = null;
        if (nm.fileInfo() == null || dialogue == null || schannel == null || (istream = dialogue.inputStream()) == null)
            return false;

        final long size = nm.fileInfo().getFilesize();
        long read = 0L;
        long rest = size;

        final int bufferSize = (int)Math.min(INT_MAX_BUFFER_SIZE, size);
        byte[] array = new byte[bufferSize];

        nm.setinbound (OUTBOUND);
        nm.setdata (array);

        print("\n");
        while (rest > 0)
        {
            read = istream.read(array, 0, bufferSize); //< блокирующая операция; вернёт -1, если достугнут конец файла
            if (read <= 0)
                break;
            rest -= read;
            nm.fileInfo().setFilesize (read);   //< пусть nm.fileInfo.filesize содержит количество считанных байтов
            print(RF_ + read);
            schannel.writeAndFlush(nm)/*.addListener(ChannelFutureListener.CLOSE)*/;
            dialogue.incChunks();
        }

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
            schannel.writeAndFlush (nm);
            lnprint("M:отправлено сообщение: "+ nm.opCode()+"; тема: "+dialogue.getTheme());
        }
    }

 //--------------------------------- EXIT ------------------------------------------------------------------------*/

//NetClient прислал задачу отправить серверу сообщение EXIT. Завершаем текущую операцию и отсылаем EXIT серверу.
    @Override public void startExitRequest (NasMsg nm)      //OUT
    {
        discardCurrentOperation();  //< скорее всего это не понадобиться, т.к. GUI блокируется на время операции
        if (nm == null && schannel != null)
        {
            nm = new NasMsg (EXIT, OUTBOUND);
            schannel.writeAndFlush (nm);
            lnprint("M:Отправлено сообщение "+OperationCodes.EXIT);
        }
    }

//прерываем текущую операцию. некоторым операциям может потребоваться особый способ прерывания.
    private void discardCurrentOperation()
    {
        if (dialogue != null)
        {
            if (dialogue.getTheme() == LOAD2LOCAL)
            {
                dialogue.discardExtruding();
            }
        }
    }

//сервер прислал EXIT. Прерываем текущую задачу и сообщаем юзеру об обрыве связи.
    @ManipulateMethod(opcodes = {EXIT})
    private void manipulateExitRequest (NasMsg nm)          //IN
    {
        lnprint("M:Получено сообщение "+OperationCodes.EXIT);
        discardCurrentOperation();
    /*  если от сервера EXIT пришёл во время выполнения запроса юзера, то opCode = ERROR заставит вызывающий метод
        стандартно обработать не(до)получение данных, а сообщение в nm.msg о разрыве соединения всё объяснит юзеру. */
        nm.setOpCode(ERROR);
        stopTalking(nm);
    }

//---------------------------------- общение с InboundHandler'ом ------------------------------------------------*/

    //Возможно, эти три метода нужно будет похерить, но это будет видно позже; сейчас они нужны серверу для
    @Override public void onChannelActive (ChannelHandlerContext ctx)
    {
        lnprint("onChannelActive(): открыто соединение: cts: "+ ctx.channel());
        callbackChannelActive.callback();   //< этого колбэка ждёт NetClient.login(), чтобы продолжить работу.
    }
    @Override public void onChannelInactive (ChannelHandlerContext ctx)
    {
        lnprint("onChannelInactive(): закрыто соединение: ctx: "+ ctx);
    }
    @Override public void onExceptionCaught (ChannelHandlerContext ctx, Throwable cause)
    {
        lnprint("onExceptionCaught(): аварийное закрытие соединения: ctx: "+ ctx);
    }

//---------------------------------- dialogue -------------------------------------------------------------------*/

//методы для создания dialogue.
    private boolean newDialogue (@NotNull NasMsg nm)
    {
        if (dialogue != null)
        {
            lnprint("M:newDialogue("+nm.opCode()+"): "+ ERROR_OLD_DIALOGUE_STILL_RUNNING );
            return false;
        }
        dialogue = new NasDialogue(nm);
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull List<FileInfo> infolist)
    {
        if (dialogue != null)
        {
            lnprint("M:newDialogue("+nm.opCode()+", infolist): "+ ERROR_OLD_DIALOGUE_STILL_RUNNING);
            return false;
        }
        if (infolist != null)
        {   dialogue = new NasDialogue(nm, infolist);
        }
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull InputStream inputStream)
    {
        if (dialogue != null)
        {
            lnprint("M:newDialogue("+nm.opCode()+", is): "+ ERROR_OLD_DIALOGUE_STILL_RUNNING);
            return false;
        }
        if (inputStream != null)
        {   dialogue = new NasDialogue(nm, inputStream);
        }
        return dialogue != null;
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull FileExtruder fe)
    {
        boolean ok = false;
        if (dialogue != null)
        {
            lnprint("M:newDialogue("+nm.opCode()+", fe, str): "+ ERROR_OLD_DIALOGUE_STILL_RUNNING);
            ok = false;
        }
        else if (fe != null)
        {
            dialogue = new NasDialogue (nm, fe);
            ok = true;
        }
        return ok;
    }

    private void stopTalking (NasMsg nm)
    {
        NasDialogue dlg = dialogue;
        if (dialogue != null)
        {
            dialogue.close();
            dialogue = null;
        }
        callbackMsgIncoming.callback(nm, dlg);
    }


//---------------------------------- другие полезные методы -----------------------------------------------------*/

//Шлём серверу сообщение об ошибке.
    public void replyWithErrorMessage()
    {
        if (dialogue != null && schannel != null)
        {
            NasMsg nm = new NasMsg (ERROR, STR_EMPTY, OUTBOUND);
            dialogue.add(nm);
            schannel.writeAndFlush (nm);
            lnprint("M:отправлено сообщение: "+ nm.opCode()+"; тема: "+dialogue.getTheme());
        }
    }

 //Составление списков методов, чтобы использовать эти спики вместо switch-case'ов.
    private void buildMethodsMaps ()
    {
        mapManipulateMetods = new HashMap<>();
        mapEndupMetods = new HashMap<>();
        Method[] methods = LocalManipulator.class.getDeclaredMethods();

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
    }

}
//---------------------------------------------------------------------------------------------------------------*/
