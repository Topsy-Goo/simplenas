package ru.gb.simplenas.server.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.annotations.EndupMethod;
import ru.gb.simplenas.common.annotations.ManipulateMethod;
import ru.gb.simplenas.common.services.Manipulator;
import ru.gb.simplenas.common.services.FileExtruder;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasDialogue;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;
import ru.gb.simplenas.server.SFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardOpenOption.*;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.structs.OperationCodes.*;
import static ru.gb.simplenas.server.SFactory.clientsListRemove;

//NasServerManipulator — это несколько больше, чем ChannelHandler, т.к. ему нужно быть посредником между сервером и
//  клиентом: хранить данные клиента, .
public class NasServerManipulator implements Manipulator
{
    private SocketChannel socketChannel;
    private String userName;
    private Path pathCurrentAbsolute; //< абсолютный путь к текущей папке пользователя
    private NasDialogue dialogue;
    private Map<OperationCodes, Method> mapManipulateMetods, mapEndupMetods;
    private static final Logger LOGGER = LogManager.getLogger(NasServerManipulator.class.getName());


    public NasServerManipulator (SocketChannel sC)      //+l
    {
        socketChannel = sC;
        buildMethodMaps();
        LOGGER.debug("создан NasServerManipulator");
    }

//---------------------------------------------------------------------------------------------------------------*/

    @Override public void handle (@NotNull ChannelHandlerContext ctx, @NotNull NasMsg nm)       //+l
    {
        LOGGER.trace("\nhandle(): start");
        if (ctx != null  &&  checkMethodInvocationContext4Handle(nm))
        {
            nm.setinbound (INBOUND);
            try {
                Method m = mapManipulateMetods.get(nm.opCode());
                if (m.getParameterCount() == 1)    m.invoke(this, nm);
                else
                if (m.getParameterCount() == 2)    m.invoke(this, nm, ctx);
                }
            catch(IllegalArgumentException | ReflectiveOperationException e){LOGGER.error("handle(): ", e);}
        }
        LOGGER.trace("handle(): end\n");
    }

//вынесли все проверки из начала метода handle() в отдельный метод. Разбрасывать этот метод по нескольким
//  методам не буду!
    private boolean checkMethodInvocationContext4Handle (NasMsg nm)     //+
    {
        if (mapManipulateMetods != null)
        if (nm != null)
        if (nm.opCode() == LOGIN || (sayNoToEmptyStrings(userName) && pathCurrentAbsolute != null))
        if (!DEBUG || nm.inbound() == OUTBOUND)
        {
            return true;
        }
        return false;
    }

    @ManipulateMethod (opcodes = {OK, ERROR})
    private void manipulateEndups (NasMsg nm)       //+
    {
        if (mapEndupMetods != null && dialogue != null)
        try {
            Method m = mapEndupMetods.get(dialogue.getTheme());
            m.invoke(this, nm);
            }
        catch(IllegalArgumentException | ReflectiveOperationException e){e.printStackTrace();}
    }

//------------------------------- LOAD2SERVER -------------------------------------------------------------------*/

    @ManipulateMethod (opcodes = {OperationCodes.LOAD2SERVER})
    private void manipulateLoad2ServerRequest (NasMsg nm)       //+l
    {
        LOGGER.trace("manipulateFileInfoRequest(): start");

        if (nm.fileInfo() == null || !sayNoToEmptyStrings (nm.msg(), nm.fileInfo().getFileName()))
        {
            LOGGER.error("manipulateFileInfoRequest(): Illegal Arguments");
        }
        else if (nm.data() == null) //< Клиент запрашивает подтверждение готовности.
        {
            if (newDialogue(nm, new ServerInboundFileExtruder()))
            {
                informClientWithOperationCode (nm, LOAD2SERVER);
            }
            else
            {   replyWithErrorMessage (null);
                stopTalking();
            }
        }
        else
        {   // Клиент Шлёт файл. Получаем первый/очередной кусочек данных. Если в процессе произойдёт ошибка,
            // то прерывать клиента не будем, чтобы не усложнять процесс, — пусть передаст всё, а потом, когда
            // от клиента придёт сообщений об окончании передачи данных, мы сообщим о результате в ответном
            // сообщении -- сделаем это в endupLoad2ServerRequest().
            dialogue.dataBytes2File(nm);
            //if (dialogue != null)   < не будем записывать эти сообщения, т.к. их запись фактически равносильна
            //    dialogue.add(nm);     записи передаваемого файла ещё и в dialogue.
            //                          TODO : убедиться, что запись не ведётся и добавить в эту проверку в тэсты.
        }
        LOGGER.trace("manipulateFileInfoRequest(): end");
    }

    private void informClientWithOperationCode (NasMsg nm, OperationCodes opcode)       //+l
    {
        if (socketChannel != null)
        {
            nm.setOpCode(opcode);
            nm.setinbound (OUTBOUND);
            if (dialogue != null)
            {
                dialogue.add(nm);
            }
            LOGGER.trace(">>>>>>>>"+nm.opCode()+">>>>>>> "+nm);
            socketChannel.writeAndFlush(nm);
        }
    }

    @EndupMethod (opcodes = {LOAD2SERVER})
    private void endupLoad2ServerRequest (NasMsg nm)        //+l
    {
        LOGGER.trace("endupLoad2ServerRequest(): start");
        boolean ok = false;
        if (dialogue != null)
        {
            dialogue.add(nm);
            if (nm.opCode() == OK  &&  dialogue.endupExtruding(nm))
            {
                informOnSuccessfulDataTrabsfer (nm);
                ok = true;
            }
        }
        if (!ok) replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM); //< это должно остаться в endupLoad2ServerRequest() как часть обработки сообщения OK от клиента
        stopTalking();
        LOGGER.trace("endupLoad2ServerRequest(): end");
    }

//------------------------------- LOAD2LOCAL --------------------------------------------------------------------*/

    private boolean canLoadToLoacal;

    @ManipulateMethod (opcodes = {LOAD2LOCAL})
    private void manipulateLoad2LocalRequest (NasMsg nm)        //+l
    {
        LOGGER.trace("manipulateLoad2LocalRequest(): start");
        if (nm.fileInfo() == null || !sayNoToEmptyStrings (nm.msg(), nm.fileInfo().getFileName()))
        {
            LOGGER.error("manipulateLoad2LocalRequest(): illegal arguments");
            return;
        }
        boolean result = false;
        canLoadToLoacal = true;

    // Файл с именем nm.fileInfo.fileName отдаём не из текущей папки, а из папки, на которую указывает nm.msg.
        FileInfo fi = nm.fileInfo();
        Path p = Paths.get (nm.msg(), fi.getFileName());
        Path valid = absolutePathToUserSpace (userName, p, fi.isDirectory());
        String errMsg = "Не удалось прочитать указанный файл. ";

    //помещаем в nm.fileInfo всю информацию, которую сервер собрал о файле.
        nm.setfileInfo (valid != null ? new FileInfo(valid) : null);

    //считываем файл и отправляем его клиенту.
        if (valid != null)
        {
            if (nm.fileInfo() == null)       errMsg += "Некорректное имя файла.";
            else
            if (!nm.fileInfo().isExists())   errMsg += "Файл не существует.";
            else
            if (nm.fileInfo().isDirectory()) errMsg += "Файл является папкой.";
            else
            if (!Files.isReadable(valid))    errMsg += "Отказано в доступе.";
            else
            {
                try (InputStream is = Files.newInputStream (valid, READ))
                {
                    result = sendFileToClient (nm, is);
                }
                catch (IOException e){e.printStackTrace();}
            }
        }
        if (result) informOnSuccessfulDataTrabsfer (nm);
        else
        replyWithErrorMessage (errMsg);
        LOGGER.trace("manipulateLoad2LocalRequest(): end");
    }

    private boolean sendFileToClient (NasMsg nm, InputStream is) throws IOException     //+l
    {
        if (is == null || !canLoadToLoacal || nm.fileInfo() == null || socketChannel == null)
        {
            LOGGER.error("sendFileToClient(): illegal arguments");
            return false;
        }
        boolean result = false;
        final long size = nm.fileInfo().getFilesize();
        long read = 0L;
        long rest = size;
        byte[] array = new byte[MAX_BUFFER_SIZE];

        nm.setinbound (OUTBOUND);
        nm.setdata(array);

        LOGGER.trace("sendAllTheFile(): начало пересылки данных >>>>>>>>>>>>>>>>");
        while (rest > 0 && canLoadToLoacal)
        {
            read = is.read (array, 0, MAX_BUFFER_SIZE); //< блокирующая операция; вернёт -1, если достугнут конец файла
            if (read <= 0)
            {
                break;
            }
            rest -= read;
            nm.fileInfo().setFilesize (read);   //< пусть nm.fileInfo.filesize содержит количество считанных байтов
            socketChannel.writeAndFlush (nm);
        }
        LOGGER.trace("sendAllTheFile(): >>>>>>>>>>>>>>>> конец пересылки данных");

        //if (size > 0)
        //    channel.flush();

        nm.fileInfo().setFilesize (size);
        result = canLoadToLoacal && rest == 0L;
        return result;
    }


//---------------------------------------------------------------------------------------------------------------*/

    @ManipulateMethod (opcodes = {LOGIN})
    private boolean manipulateLoginRequest (@NotNull NasMsg nm, ChannelHandlerContext ctx)      //+l
    {
        LOGGER.trace("manipulateLoginRequest(): start");
        if (userName != null)
        {
            LOGGER.debug("manipulateLoginRequest(): userName = "+ userName);
        }
        boolean result = false;
        String  name = nm.msg();
        String  errMsg = ERROR_UNABLE_TO_PERFORM;

        if (!checkUserNameValid (name) || !SFactory.clientsListAdd(this, name))
        {
            errMsg = String.format("Авторизация отклонена. Возможно, пользователь\n%s\nуже подключен.", name);
        }
        else
        {
            pathCurrentAbsolute = constructAbsoluteUserPath (name);
            if (checkUserFolder (name) && pathCurrentAbsolute != null)
            {
                userName = name;
                informClientWithOperationCode (nm, OK);
                result = true;
            }
        }
        if (!result)
        {
            replyWithErrorMessage (errMsg);
            socketChannel.close();
            ctx.disconnect();//close(); //TODO : соединение завершают оба вызова.
        }
        LOGGER.trace("manipulateLoginRequest(): end");
        return result;
    }

    @ManipulateMethod (opcodes = {OperationCodes.FILEINFO})
    private void manipulateFileInfoRequest (NasMsg nm)      //+l
    {
        LOGGER.trace("manipulateFileInfoRequest(): start");
        boolean ok = false;
        if (nm.fileInfo() != null && sayNoToEmptyStrings (nm.msg(), nm.fileInfo().getFileName()))
        {
            FileInfo fi = getSafeFileInfo (userName, nm.msg(), nm.fileInfo().getFileName());
            if (fi != null)
            {
                nm.setfileInfo(fi);
                informClientWithOperationCode (nm, OK);
                ok = true;
            }
        }
        if (!ok) replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM);
        LOGGER.trace("manipulateFileInfoRequest(): end");
    }

    @ManipulateMethod (opcodes = {OperationCodes.COUNTITEMS})
    private void manipulateCountEntriesRequest (NasMsg nm)      //+l
    {
        LOGGER.trace("manipulateCountEntriesRequest(): start");
        int result = -1;
        if (nm.fileInfo() != null && sayNoToEmptyStrings (nm.msg(), nm.fileInfo().getFileName()))
        {
            Path p = Paths.get(nm.msg(), nm.fileInfo().getFileName());
            Path valid = absolutePathToUserSpace (userName, p, nm.fileInfo().isDirectory());

            if (valid != null)
            {
                result = countDirectoryEntries (valid);
            }
            LOGGER.trace("manipulateCountEntriesRequest(): папка <"+p.toString()+"> содержит "+result+" элементов.");
            if (result >= 0)
            {
                nm.fileInfo().setFilesize (result);
                informClientWithOperationCode (nm, OK);
            }
        }
        if (result < 0) replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM);
        LOGGER.trace("manipulateCountEntriesRequest(): end");
    }

    @ManipulateMethod (opcodes = {OperationCodes.CREATE})
    private void manipulateCreateRequest (NasMsg nm)        //+l
    {
        LOGGER.trace("manipulateCreateRequest(): start");
        boolean ok = false;
        if (sayNoToEmptyStrings(nm.msg()))
        {
            nm.setfileInfo (createSubfolder4User (pathCurrentAbsolute, userName, nm.msg()));
            if (nm.fileInfo() != null)
            {
                nm.setmsg (relativizeByUserName (userName, pathCurrentAbsolute).toString());   //< эту строку клиент должен отобразить в соотв. поле ввода.
                informClientWithOperationCode (nm, OK);
                ok = true;
            }
        }
        if (!ok) replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM);
        LOGGER.trace("manipulateCreateRequest(): end");
    }

    @ManipulateMethod (opcodes = {OperationCodes.RENAME})
    private void manipulateRenameRequest (NasMsg nm)        //+l
    {
        LOGGER.trace("manipulateRenameRequest(): start");
        boolean ok = false;
        if (sayNoToEmptyStrings(nm.msg()) && nm.fileInfo() != null)
        {
            String newName = nm.msg();
            FileInfo fi = rename (pathCurrentAbsolute, nm.fileInfo().getFileName(), newName);
            if (fi != null)
            {
                nm.setmsg (relativizeByUserName (userName, pathCurrentAbsolute).toString());   //< эту строку клиент должен отобразить в соотв. поле ввода.
                informClientWithOperationCode (nm, OK);
                ok = true;
            }
        }
        if (!ok) replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM);
        LOGGER.trace("manipulateRenameRequest(): end");
    }

    @ManipulateMethod (opcodes = {OperationCodes.DELETE})
    private void manipulateDeleteRequest (NasMsg nm)        //+l
    {
        LOGGER.trace("manipulateDeleteRequest(): start");
        boolean result = false;
        if (nm.fileInfo() != null && sayNoToEmptyStrings (nm.msg(), nm.fileInfo().getFileName()))
        {
            Path p = Paths.get (nm.msg(), nm.fileInfo().getFileName());
            Path valid = absolutePathToUserSpace (userName, p, nm.fileInfo().isDirectory());

            if (result = valid != null && deleteFileOrDirectory (valid))
            {
                informClientWithOperationCode (nm, OK);
            }
        }
        if (!result) replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM);
        LOGGER.trace("manipulateDeleteRequest(): end");
    }

//---------------------------------- LIST -----------------------------------------------------------------------*/

//список элементов запрошенной папки отсылаем клиенту отдельными сообщениями (запрошеный относительный путь — в nm.msg).
    @ManipulateMethod (opcodes = {LIST})
    private void manipulateListRequest (@NotNull NasMsg nm)      //+l
    {
        LOGGER.trace("manipulateListRequest(): start");
        if (socketChannel != null)
        {
            Path valid = absolutePathToUserSpace (userName, Paths.get(nm.msg()), FOLDER);
            if (valid != null && Files.exists(valid))
            {
                pathCurrentAbsolute = valid;
                sendFileInfoList (listFolderContents (valid));

                nm.setmsg (relativizeByUserName (userName, valid).toString());  //< эту строку клиент должен отобразить в соотв. поле ввода.
                informClientWithOperationCode (nm, OK);
            }
            else replyWithErrorMessage(SFactory.ERROR_INVALID_FILDER_SPECIFIED);
        }
        LOGGER.trace("manipulateListRequest(): end");
    }

    private void sendFileInfoList (@NotNull List<FileInfo> flist)     //+l
    {
        if (socketChannel != null && flist != null)
        {
            NasMsg newnm = new NasMsg (LIST, null, OUTBOUND);
            int counter = 0;
            for (FileInfo fi : flist)
            {
                newnm.setfileInfo (fi);
                counter ++;
                LOGGER.trace("sendFileInfoList(): >>>>>>>>>>>>>>>> "+ newnm.toString());
                socketChannel.writeAndFlush (newnm);
            }
            LOGGER.debug("sendFileInfoList(): отправлено сообщений: "+ counter);
        }
    }


//---------------------------------------------------------------------------------------------------------------*/

// информирует клиента об успешно оправке или об успешном получении файла.
    private void informOnSuccessfulDataTrabsfer (NasMsg nm)     //+
    {
        nm.setdata (null);
        informClientWithOperationCode (nm, OK);
    }

//Отсылем клиенту сообщение об ошибке.
    public void replyWithErrorMessage (String errMsg)       //+l
    {
        if (socketChannel != null)
        {
            if (errMsg == null)
            {
                errMsg = ERROR_UNABLE_TO_PERFORM;
            }
            NasMsg nm = new NasMsg (ERROR, errMsg, OUTBOUND);

            if (dialogue != null)
            {
                dialogue.add(nm);
            }
            LOGGER.trace("replyWithErrorMessage() >>>>>>>>"+nm.opCode()+">>>>>>> "+nm);
            socketChannel.writeAndFlush (nm);
        }
    }

//пара методов для создания dialogue.
    private void newDialogue (@NotNull NasMsg nm)   //+
    {
        if (dialogue != null)
        {
            replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM);
        }
        else if (nm != null)
        {
            dialogue = new NasDialogue(nm);
        }
    }

    private boolean newDialogue (@NotNull NasMsg nm, @NotNull FileExtruder tc)      //+l
    {
        boolean ok = false;
        if (dialogue != null)
        {
            replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM);
        }
        else if (nm != null && tc != null && sayNoToEmptyStrings(userName))
        {
            dialogue = new NasDialogue(nm, tc);
            ok = dialogue.initializeFileExtruder(nm, userName);
        }
        else LOGGER.error("newDialogue(): illegal arguments");
        return ok;
    }

//закрываем dialogue
    private void stopTalking()      //+
    {
        LOGGER.trace("\t\t\t\tstopTalking() call");
        if (dialogue != null)
        {
            dialogue.close();
        }
        dialogue = null;
    }

//Возможно, эти три метода нужно будет похерить, но это будет видно позже.
    @Override public void onChannelActive (@NotNull ChannelHandlerContext ctx)      //+l
    {
        LOGGER.trace("onChannelActive(): открыто соединение: ctx: "+ ctx);
        if (socketChannel == null)
        {
            socketChannel = (SocketChannel) ctx.channel();
        }
    }
    @Override public void onChannelInactive (@NotNull ChannelHandlerContext ctx)        //+l
    {
        LOGGER.trace("onChannelInactive(): закрыто соединение: ctx: "+ ctx);
        if (userName != null)
        {
            clientsListRemove(this, userName);
            userName = null;
        }
    }
    @Override public void onExceptionCaught (@NotNull ChannelHandlerContext ctx, @NotNull Throwable cause)      //+l
    {
        LOGGER.trace("onExceptionCaught(): аварийное закрытие соединения: ctx: "+ ctx);
        if (userName != null)
        {
            clientsListRemove(this, userName);
            userName = null;
        }
    }


//---------------------------------------------------------------------------------------------------------------*/

//эти методы нужны NetClient'у для общения с Manipulator'ом.
    @Override public boolean startLoad2LocalRequest (String toLocalFolder, NasMsg nm)  {LOGGER.error("startLoad2LocalRequest(): Unsupported Operation");  return false;}
    @Override public boolean startLoad2ServerRequest (String toServerFolder, NasMsg nm){LOGGER.error("startLoad2ServerRequest(): Unsupported Operation"); return false;}
    @Override public boolean startListRequest (NasMsg nm)   {LOGGER.error("startListRequest(): Unsupported Operation");   return false;}
    @Override public boolean startSimpleRequest (NasMsg nm) {LOGGER.error("startSimpleRequest(): Unsupported Operation"); return false;}

//----------------------------------- EXIT ----------------------------------------------------------------------*/

//Сообщаем клиенту, что мы разрываем соединение.
    @Override public void startExitRequest (NasMsg nm)      //+l
    {
        LOGGER.trace("startExitRequest(): start");
        if (socketChannel != null)
        {
            discardCurrentOperation();
            if (nm == null)
            {   nm = new NasMsg (OperationCodes.EXIT, PROMPT_CONNECTION_GETTING_CLOSED, OUTBOUND);
            }
            socketChannel.writeAndFlush (nm);
            socketChannel.disconnect();
        }
        LOGGER.trace("startExitRequest(): end");
    }

//обработка сообщения от клиента
    @ManipulateMethod (opcodes = {OperationCodes.EXIT})
    private void manipulateExitRequest (NasMsg nm, ChannelHandlerContext ctx)       //+l
    {
        LOGGER.trace("manipulateExitRequest(): start");
        if (sayNoToEmptyStrings(userName) && socketChannel != null)
        {
            discardCurrentOperation();
            clientsListRemove (this, userName);
            socketChannel.disconnect();
            ctx.disconnect();//close(); //TODO : соединение завершают оба вызова.
        }
        LOGGER.trace("manipulateExitRequest(): end");
    }

//вызывается из обработчиков EXIT с целью прервать текущую операцию
    private void discardCurrentOperation()      //+
    {
        if (dialogue != null)
        {
            switch (dialogue.getTheme())
            {
                case LOAD2SERVER:
                    dialogue.discardExtruding();
                    replyWithErrorMessage (ERROR_UNABLE_TO_PERFORM);
                    break;
                case LOAD2LOCAL:
                    canLoadToLoacal = false;
                    break;
            }
            stopTalking();
        }
    }

    private void buildMethodMaps()      //+l
    {
        LOGGER.trace("buildMethodMaps(): start");
        mapManipulateMetods = new HashMap<>();
        mapEndupMetods = new HashMap<>();
        Method[] methods = NasServerManipulator.class.getDeclaredMethods ();

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

//---------------------------------------------------------------------------------------------------------------*/

    //void test () throws IOException
    //{
    //    OutputStream os = new BufferedOutputStream(new FileOutputStream("ssss",true));
    //    SeekableByteChannel sbc = Files.newByteChannel(Paths.get("sss"), CREATE_NEW);
    //    sbc.write(null);
    //    sbc.size();
    //}

}// class NasServerManipulator

    //NasMsg newnm = buildNasMsg (OperationCodes.TEST, p.getFileName().toString(), OUTBOUND);
    //channel.writeAndFlush (newnm);
    //java/nio/file/StandardOpenOption.java