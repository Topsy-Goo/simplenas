package ru.gb.simplenas.server.services.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import ru.gb.simplenas.common.annotations.EndupMethod;
import ru.gb.simplenas.common.annotations.ManipulateMethod;
import ru.gb.simplenas.common.services.Manipulator;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasDialogue;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;
import ru.gb.simplenas.server.SFactory;
import ru.gb.simplenas.server.services.ServerFileManager;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.Factory.printf;
import static ru.gb.simplenas.common.services.impl.NasFileManager.*;
import static ru.gb.simplenas.common.structs.NasDialogue.NasDialogueForDownloading;
import static ru.gb.simplenas.common.structs.NasDialogue.NasDialogueForUploading;
import static ru.gb.simplenas.common.structs.OperationCodes.*;
import static ru.gb.simplenas.common.structs.OperationCodes.NM_OPCODE_CREATE;
import static ru.gb.simplenas.server.SFactory.*;

/**
<b>Краткое описание протокола общения клиента и сервера.</b><p>
Сервер и клиент обмениваются сообщениями способом, описанным ниже в этом комментарии. Будем называть этот способ протоколом. Обе стороны должны придерживаться этого протокола передачи данных. Абстрагируясь назовём отправителя сообщения А, а получателя — Б.<p>

Передачи данных делятся на диалоги (сеансы), каждый диалог может состоять из одного или нескольких сообщений. Новый диалог не может начаться до завершения предыдущего диалога.<p>

Для передачи сообщений используется объект (структура) NasMsg, которая содержит всю необходимую информацию о сообщении, и может даже содержать передаваемые данные, если их объём невелик. NasMsg также содержит код сообщения, который мы будем иногда называть темой сообщения.<p>

Первое сообщение каждого диалога задаёт тему диалога, и эта тема должна оставаться неизменной для всех сообщений в рамках диалога, кроме последнего сообщения. Т.е. все сообщения диалога (независимо от направления), кроме последнего сообщения, должны иметь один и тот же код. Последнее сообщение в рамках диалога называется терминальным и может иметь код OK или ERROR. Получив (или отправив) терминальное сообщение OK или ERROR, сторона (А или Б) не имеет права продолжать диалог.<p>

Если обмен данными может уместиться в единственное сообщение, то терминальное сообщение не является обязательным. Но обе стороны должны уметь обрабатывать такие сообщения (должны знать, что терминальное сообщение с кодом OK или ERROR не придёт). Примером такого сообщения является EXIT. По сути, такие сообщения можно рассматривать как терминальные.<p>

Чтобы не запутаться в способах обработки сообщений в рамках диалога, стороны А и Б могут вести собственные «логи» диалога. В роли такого лога выступает объект (структура) NasDialogue.<p>

<b>О добавлении новых обработчиков сообщений.</b><p>

Чтобы добавить новый обработчик сообщения, нужно соотв. ему метод пометить аннотацией<br>
 {@code @ManipulateMethod (opcodes = {FRIDAY, EVENING})},<br>
 где FRIDAY и EVENING — коды сообщений, которые будут закреплены за новым обработчиком. Для добавления обработчиков терминальных сообщений используется тот же принцип, т.к. терминальные сообщения принимаются аналогично другим сообщениям.<p>

Если наша сторона рассчитывает также принимать и терминальное сообщение OK или ERROR по темам FRIDAY или EVENING, то нужно указать обработчик и для этого случая. Принцип следующий: терминальные сообщения OK и ERROR используются многими темами при завершении, поэтому обработчик сообщений OK или ERROR должен иметь возможность узнать, какую тему он завершает и какой метод нужно вызвать для завершения темы. В этом ему помогают NasDialogue, который содержит тему диалога, и список {@code
 mapEndupMetods}, который содержит методы для завершения диалогов на различные темы.<p>

Чтобы добавить метод в список {@code mapEndupMetods}, нужно снабдить его анотацией<br>{@code @EndupMethod (opcodes = {FRIDAY, EVENING})}.<br>Применительно к рассматриваемому выше примеру это означает, что помеченный т.о. метод должен обрабатывать терминальные сообщения OK или ERROR для тем FRIDAY и EVENING.<p>

Теперь, диспетчер сообщений на нашей стороне будет направлять все сообщения по темам FRIDAY или EVENING в метод, помеченный аннотацией {@code @ManipulateMethod (opcodes = {FRIDAY, EVENING})} (и находящийся в списке mapManipulateMetods). Но кода диалоги по этим темам завершаться сообщениями OK или ERROR, то диспетчер отправит сообщение в обработчик OK или ERROR, а тот, в свою очередь, извлечёт из списка mapEndupMetods метод, помеченный аннотацией {@code @EndupMethod (opcodes = {FRIDAY, EVENING})}, и даст ему обработать завершение соотв.диалога.<p>

 У каждого сообщения может быть только один обработчик «обычных» сообщений и только один обработчик терминальных сообщений.
*/
public class RemoteManipulator implements Manipulator {

    private static final Logger LOGGER = LogManager.getLogger(RemoteManipulator.class.getName());
    private              Map<OperationCodes, Method> mapManipulateMetods;
    private              Map<OperationCodes, Method> mapEndupMetods;

    private final ServerFileManager sfm;
    private       SocketChannel     socketChannel;
    private       String            userName;
    private       Path              pathCurrentAbsolute; //< абсолютный путь к текущей папке пользователя
    private       NasDialogue       dialogue;

//---------------------------------------------------------------------------------------------------*/

    public RemoteManipulator (ServerFileManager sfm, SocketChannel socketChannel) {
        this.sfm = sfm;
        this.socketChannel = socketChannel;
        buildMethodsMaps();
        LOGGER.debug("создан RemoteManipulator");
    }

    @Override public void handle (ChannelHandlerContext ctx, NasMsg nm) throws IOException
    {
        if (ctx != null  &&  checkMethodInvocationContext4Handle(nm)) {
            nm.setinbound (INBOUND);
            OperationCodes opcode = nm.opCode();
            String methodName = null;
            try {
                Method m = mapManipulateMetods.get (nm.opCode());
                if (m == null)
                    throw new UnsupportedOperationException (
                                sformat ("Нет метода для кода операции: «%s».", opcode));

                methodName = m.getName();

                if (m.getParameterCount() == 1)
                    m.invoke (this, nm);
                else if (m.getParameterCount() == 2)
                    m.invoke (this, nm, ctx);
            }
            catch (IllegalArgumentException | ReflectiveOperationException e) {
                LOGGER.error("handle(): метод "+ methodName +" вызвал ошибку: \n", e);
            }
        }
    }

/** вынесли все проверки из начала метода handle() в отдельный метод. Разбрасывать этот метод по нескольким
  методам не буду!    */
    private boolean checkMethodInvocationContext4Handle (NasMsg nm) {
        return (mapManipulateMetods != null
                && (nm != null)
                && (nm.opCode() == NM_OPCODE_LOGIN
                    || (sayNoToEmptyStrings (userName) && pathCurrentAbsolute != null))
                && (!DEBUG
                    || nm.inbound() == OUTBOUND));
    }

    @ManipulateMethod (opcodes = {NM_OPCODE_OK, NM_OPCODE_ERROR})
    private void manipulateEndups (NasMsg nm) {

        if (mapEndupMetods != null && dialogue != null)
        try {
            OperationCodes opcode = dialogue.getTheme();
            Method m = mapEndupMetods.get (opcode);
            if (m == null)
                throw new UnsupportedOperationException (
                            sformat ("Нет метода для кода операции: «%s».", opcode));
            m.invoke (this, nm);
        }
        catch (IllegalArgumentException | ReflectiveOperationException e) {e.printStackTrace();}
    }

//------------------------------- LOAD2SERVER ---------------------------------------------------*/

/** Обрабатываем получение запроса от клиента на передачу файла от клиента к серверу.<p>
    Папка назначения на стороне сервера не обязана быть текущей папкой, но обязана находиться
    внутри дискового простарнства юзера (ДПП).<p>
    Признаком начала запроса считается условие NasMsg.data == null.<p>

    NasMsg.msg — относит. путь к папке на стороне сервера (в самом начале, когда NasMsg.data == null);
    в остальных случаях это поле содержит уточняющий код операции.<p>
    NasMsg.fileInfo == вся остальная информация о файле.
*/
    @ManipulateMethod (opcodes = NM_OPCODE_LOAD2SERVER)
    private void manipulateLoad2ServerRequest (NasMsg nm) throws IOException {
        String errorMessage = null;

    //если nm.data == null, то это означает, что клиент запрашивает подтверждение готовности:
        if (nm.data() == null) {
            if (dialogue == null)
                errorMessage = inlinePrepareToDownloading (nm);
            else
                replyWithErrorMessage (nm, sformat ("Сервер занят выполнением операции: %s.",
                                                    dialogue.getTheme()));
        }
        else if (dialogue != null)
            errorMessage = inlineDownloading (nm);
        else
            errorMessage = ERROR_UNABLE_TO_PERFORM;

        if (errorMessage != null) {
            replyWithErrorMessage (nm, errorMessage);
            stopTalking (errorMessage);
        }
    }

//Подставляемая функция для manipulateLoad2ServerRequest (nm).
    private String inlinePrepareToDownloading (NasMsg nm) throws IOException
    {
        String errorMessage = null;
        Path ptargetfile = getSafeTargetFilePath (nm);
        if (ptargetfile != null) {
            dialogue = NasDialogueForDownloading (nm, ptargetfile);
            nm.setmsg (NM_OPCODE_READY.name());
            senddata(nm);
            printf ("\nПринимаем файл <%s>\n", dialogue.tc.path);
        }
        else errorMessage = ERROR_INVALID_FOLDER_SPECIFIED;
        return errorMessage;
    }

//Подставляемая функция для manipulateLoad2ServerRequest (nm).
    private String inlineDownloading (NasMsg nm) throws IOException
    {
        String errorMessage = null;
        switch (OperationCodes.valueOf (nm.msg()))
        {
            //case NM_OPCODE_READY: {}break;
            case NM_OPCODE_DATA:  {  //клиент передаёт части файла:
                if (dialogue.tc.fileDownloadAndWrite (nm))
                    sendempty (nm);
                else errorMessage = sformat ("Не удалось принять файл: <%s>.", dialogue.tc.path);
            }break;
            case NM_OPCODE_OK:    {    //клиент закончил передавать данные:
                if (dialogue.tc.endupDownloading (nm)) {
                    nm.setmsg (NM_OPCODE_OK.name());
                    senddata (nm);
                    stopTalking (sformat ("\nПринят файл <%s>.\n", dialogue.tc.path));
                }
                else errorMessage = "Не удалось сохранить полученный файл.";
            }break;
            case NM_OPCODE_EXIT:  {    //нужно разорвать соединение, не закончив передавать файл:
                dialogue.discardExtruding();
                manipulateExitRequest (nm);
            }break;
            case NM_OPCODE_ERROR: { //клиент сообщил об ошибке
                stopTalking (sformat ("Не удалось принять файл: <%s>.", dialogue.tc.path));
            }break;
            default: {  //неизвестный код операции:
                errorMessage = sformat ("Неизвестный код операции: %s", nm.msg());
                if (DEBUG) throw new UnsupportedOperationException (errorMessage);
            }
        }//switch
        return errorMessage; //< Это сообщение об ошибке будет отправлено клиенту.
    }

/** Метод составляет полное имя файла, считая, что в NasMsg.msg == путь к файлу, а
    NasMsg.fileInfo.fileName == имя файла. Отсутствие указанной папки считается ошибкой.
    Отсутствие файла НЕ считается ошибкой.<p>
    Затем метод убеждается, что полученный т.о. путь указывает внутрь дискового пространства
    пользователя.
    @return путь к файлу, если всё прошло хорошо, или NULL в случае ошибки.
*/
    private Path getSafeTargetFilePath (NasMsg nm) {
        Path ptargetfile = null;
        if (nm != null && nm.fileInfo() != null) {
            String fileName   = nm.fileInfo().getFileName();
            String folderName = nm.msg();

            if (sayNoToEmptyStrings (userName, folderName, fileName)) {
                Path pRequestedTarget = Paths.get (folderName, fileName);
                ptargetfile = sfm.absolutePathToUserSpace (userName, pRequestedTarget,
                                                           nm.fileInfo().isDirectory());
            }
        }
        return ptargetfile;
    }

//------------------------------- LOAD2LOCAL -----------------------------------------------------

/** Обрабатываем получение запроса от клиента на передачу файла от сервера к клиенту.<p>
    Запрошенный файл не обязан находиться в текущей папке, но обязан находиться внутри
    дискового простарнства юзера (ДПП).<p>
    Признаком начала запроса считается условие NasMsg.data == null.<p>
    NasMsg.msg == имя папки-источника на стороне сервера (в самом начале, когда NasMsg.data == null);
    в остальных случаях это поле содержит уточняющий код операции.<p>
    NasMsg.fileInfo == вся остальная информация о файле.
*/
    @ManipulateMethod (opcodes = NM_OPCODE_LOAD2LOCAL)
    private void manipulateLoad2LocalRequest (NasMsg nm) throws IOException {

        if (nm.data() == null)    //< клиент изъявил желание загрузить файл с сервера:
        {
            if (dialogue == null)
                inlinePrepareToUploading (nm);
            else
                replyWithErrorMessage (nm, sformat ("Сервер занят выполнением задачи: %s.", dialogue.getTheme()));
        }
        else if (dialogue != null)
            inlineUploading (nm);
        else {
            String errMsg = "\nОШИБКА: dialogue == null.\n";
            replyWithErrorMessage (nm, errMsg);
            if (DEBUG) throw new RuntimeException (errMsg);    else LOGGER.error (errMsg);
        }
    }

//Подставляемая функция для manipulateLoad2LocalRequest (nm).
    private void inlinePrepareToUploading (NasMsg nm) throws IOException
    {
        boolean  result = false;
        FileInfo fi = nm.fileInfo();
        String   strPath = nm.msg();
        String   strName;
        String   errMsg  = "Не удалось прочитать указанный файл. ";

        if (fi == null || !sayNoToEmptyStrings (strName = fi.getFileName(), strPath))
            errMsg += "Некорректное имя файла";
        else if (fi.isDirectory())
            errMsg += "Файл является папкой.";
        else {
            Path p      = Paths.get (strPath, strName);
            Path valid  = sfm.absolutePathToUserSpace (userName, p, NOT_FOLDER);

            if (valid == null || !isFileExists (valid))
                errMsg += "Файл не существует.";
            else if (!isReadable (valid))
                errMsg += "Отказано в доступе.";
            else {
                fi.setExists (true);
                fi.setFilesize (fileSize (valid));

                dialogue = NasDialogueForUploading (nm, valid);
                nm.setdata (null);
                nm.setmsg (NM_OPCODE_READY.name());
                senddata(nm);
                result = true;
            }
        }
        if (!result)
            replyWithErrorMessage (nm, errMsg);
    }

//Подставляемая функция для manipulateLoad2LocalRequest (nm).
    private void inlineUploading (NasMsg nm) throws IOException
    {
        OperationCodes opcode = OperationCodes.valueOf (nm.msg());
        switch (opcode)
        {
            case NM_OPCODE_READY: {    //клиент сообщил о готовности принимать файл:
                printf ("\nОтдаём файл <%s>\n", dialogue.tc.path);
                nm.setmsg (NM_OPCODE_DATA.name());
                dialogue.tc.prepareToUpload (nm);
                //dialogue.tc.fileReadAndUpload (nm, funcSendNasMsg());
            }//break;
            case NM_OPCODE_DATA:  {
                if (dialogue.tc.rest > 0L)
                    dialogue.tc.fileReadAndUpload (nm, funcSendNasMsg());
                else {
                    if (dialogue.tc.rest == 0L) {
                        nm.setmsg (NM_OPCODE_OK.name());
                        sendempty (nm);
                        stopTalking (sformat ("Отдан файл: <%s>.", dialogue.tc.path));
                    }
                    else {
                        String errMsg = sformat ("Ошибка во время выполнения %s / %s : rest = %d.",
                                                 dialogue.getTheme(), nm.msg(), dialogue.tc.rest);
                        replyWithErrorMessage (nm, errMsg);
                        stopTalking (errMsg);
                        if (DEBUG) throw new RuntimeException (errMsg);
                    }
                }
            }break;
            case NM_OPCODE_OK:    {    //клиент подтвердил успешное завершение передачи:
                stopTalking (sformat ("Отдан файл <%s>\n", dialogue.tc.path));
            }break;
            case NM_OPCODE_ERROR: {    //клиент сообщил об ошибке:
                stopTalking (sformat ("Не удалось отдать файл: <%s>.\n", dialogue.tc.path));
            }break;
            case NM_OPCODE_EXIT:  {
                //нужно разорвать соединение, не дожидаясь окончания отдачи файла (мы не знаем,
                // откуда сообщение пришло, поэтому дублируем его клиенту):
                manipulateExitRequest (nm);
            }break;
            default: {    //неизвестный код сообщения
                String errMsg = sformat ("Неизвестный код операции: %s. Операция %s прервана.",
                                         nm.msg(), dialogue.getTheme());
                replyWithErrorMessage (nm, errMsg);
                stopTalking (errMsg);
                if (DEBUG) throw new UnsupportedOperationException (errMsg);
            }
        }//switch
    }

//---------------------------- LOGIN, FILEINFO, COUNTITEMS, CREATE, RENAME, DELETE ------------------------------*/

    @ManipulateMethod (opcodes = NM_OPCODE_LOGIN)
    private boolean manipulateLoginRequest (@NotNull NasMsg nm, ChannelHandlerContext ctx) {

        boolean result   = false;
        String  name     = nm.msg();
        String  password = (String) nm.data();
        String  errMsg   = ERROR_UNABLE_TO_PERFORM;

/*  в приложении отсутствует механизм регистрации пользователей.
    Приложение умеет только проверять правильность логина и пароля.
*/
        if (userName != null) {
            LOGGER.debug("manipulateLoginRequest(): userName = " + userName);
        }
        else if (!isNameValid (name)) {
            errMsg = sformat (ERR_FORMAT_UNALLOWABLE_USERNAME, name);
        }
        else if (!validateOnLogin (name, password)) {
            errMsg = sformat (ERR_FORMAT_NOT_REGISTERED, name);
        }
        else if (!clientsListAdd (this, name)) {
            errMsg = sformat (ERR_FORMAT_LOGIN_REJECTED, name);
        }
        else {
            pathCurrentAbsolute = sfm.constructAbsoluteUserRoot (name);
            if (sfm.checkUserFolder (name) && pathCurrentAbsolute != null) {
                userName = name;
                informClientWithOperationCode (nm, NM_OPCODE_OK);
                printf ("\nПодключен клиент: <%s>.\n", userName);
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

    @ManipulateMethod (opcodes = NM_OPCODE_FILEINFO)
    private void manipulateFileInfoRequest (NasMsg nm) {

        boolean ok = false;
        if (nm.fileInfo() != null && sayNoToEmptyStrings(nm.msg(), nm.fileInfo().getFileName())) {

            FileInfo fi = sfm.getSafeFileInfo(userName, nm.msg(), nm.fileInfo().getFileName());
            if (fi != null) {
                nm.setfileInfo(fi);
                informClientWithOperationCode(nm, NM_OPCODE_OK);
                ok = true;
            }
        }
        if (!ok) replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM);
    }

    @ManipulateMethod (opcodes = NM_OPCODE_COUNTITEMS)
    private void manipulateCountEntriesRequest (NasMsg nm) {

        long result = -1;
        if (nm.fileInfo() != null && sayNoToEmptyStrings(nm.msg(), nm.fileInfo().getFileName())) {

            Path p     = Paths.get(nm.msg(), nm.fileInfo().getFileName());
            Path valid = sfm.absolutePathToUserSpace (userName, p, nm.fileInfo().isDirectory());

            if (valid != null) {
                result = sfm.safeCountDirectoryEntries (valid, userName);
            }
            if (result >= 0) {
                nm.fileInfo().setFilesize (result);
                informClientWithOperationCode(nm, NM_OPCODE_OK);
            }
        }
        if (result < 0) replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM);
    }

    @ManipulateMethod (opcodes = NM_OPCODE_CREATE)
    private void manipulateCreateRequest (NasMsg nm) {

        boolean ok = false;
        if (sayNoToEmptyStrings(nm.msg())) {

            nm.setfileInfo(sfm.createSubfolder4User(pathCurrentAbsolute, userName, nm.msg()));
            if (nm.fileInfo() != null) {

                nm.setmsg(sfm.relativizeByUserName(userName, pathCurrentAbsolute).toString());   //< эту строку клиент должен отобразить в соотв. поле ввода.
                informClientWithOperationCode(nm, NM_OPCODE_OK);
                ok = true;
            }
        }
        if (!ok) replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM);
    }

    @ManipulateMethod (opcodes = NM_OPCODE_RENAME)
    private void manipulateRenameRequest (NasMsg nm) {

        boolean ok = false;
        if (sayNoToEmptyStrings(nm.msg()) && nm.fileInfo() != null) {

            String   newName = nm.msg();
            FileInfo fi      = sfm.safeRename(pathCurrentAbsolute, nm.fileInfo().getFileName(), newName, userName);
            if (fi != null) {

                nm.setmsg(sfm.relativizeByUserName(userName, pathCurrentAbsolute).toString());   //< эту строку клиент должен отобразить в соотв. поле ввода.
                informClientWithOperationCode(nm, NM_OPCODE_OK);
                ok = true;
            }
        }
        if (!ok) replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM);
    }

    @ManipulateMethod (opcodes = NM_OPCODE_DELETE)
    private void manipulateDeleteRequest (NasMsg nm) {

        boolean result = false;
        if (nm.fileInfo() != null && sayNoToEmptyStrings(nm.msg(), nm.fileInfo().getFileName())) {

            Path p     = Paths.get(nm.msg(), nm.fileInfo().getFileName());
            Path valid = sfm.absolutePathToUserSpace(userName, p, nm.fileInfo().isDirectory());

            if (result = valid != null && sfm.safeDeleteFileOrDirectory(valid, userName)) {
                informClientWithOperationCode(nm, NM_OPCODE_OK);
            }
        }
        if (!result) replyWithErrorMessage(ERROR_UNABLE_TO_PERFORM);
    }

//---------------------------------- LIST -----------------------------------------------------------------------*/
/** список элементов запрошенной папки отсылаем клиенту отдельными сообщениями (запрошеный относительный путь — в nm.msg).    */
    @ManipulateMethod (opcodes = NM_OPCODE_LIST)
    private void manipulateListRequest (@NotNull NasMsg nm) {

        if (socketChannel != null) {
            Path valid = sfm.absolutePathToUserSpace(userName, Paths.get(nm.msg()), FOLDER);
            if (valid != null && Files.exists(valid)) {

                pathCurrentAbsolute = valid;
                sendFileInfoList (listFolderContents(valid));

                nm.setmsg (sfm.relativizeByUserName (userName, valid).toString());  //< эту строку клиент должен отобразить в соотв. поле ввода.
                informClientWithOperationCode (nm, NM_OPCODE_OK);
            }
            else replyWithErrorMessage(SFactory.ERROR_INVALID_FOLDER_SPECIFIED);
        }
    }

    private void sendFileInfoList (@NotNull List<FileInfo> flist) {
        if (socketChannel != null && flist != null) {

            NasMsg newnm   = new NasMsg (NM_OPCODE_LIST, null, OUTBOUND);
            int    counter = 0;
            for (FileInfo fi : flist) {
                newnm.setfileInfo(fi);
                counter++;
                socketChannel.writeAndFlush(newnm);
            }
            LOGGER.debug("sendFileInfoList(): отправлено сообщений: " + counter);
        }
    }

//---------------------------------------------------------------------------------------------------------------*/
/** информирует клиента об успешно оправке или об успешном получении файла.  */
    private void informOnSuccessfulDataTrabsfer (NasMsg nm) {
        nm.setdata(null);
        informClientWithOperationCode(nm, NM_OPCODE_OK);
    }

/** Отсылем клиенту сообщение об ошибке.  */
    public void replyWithErrorMessage (String errMsg) {
        if (socketChannel != null) {
            if (errMsg == null)
                errMsg = ERROR_SERVER_UNABLE_TO_PERFORM;
            NasMsg nm = new NasMsg (NM_OPCODE_ERROR, errMsg, OUTBOUND);
            socketChannel.writeAndFlush(nm);
        }
    }

/** Отвечаем клиенту сообщением об ошибке. При этом:<br>
    • <u>код операции не меняется</u>,<br>
    • код ошибки — NM_OPCODE_ERROR — помещается в NasMsg.msg,<br>
    • текст сообщения — в NasMsg.data.<p>
    Получатель должен знать, что ему приходят сообщения в именно таком формате. */
    private void replyWithErrorMessage (NasMsg nm, String message) {
        if (socketChannel != null) {
            if (message == null)
                message = ERROR_SERVER_UNABLE_TO_PERFORM;
            nm.setmsg (NM_OPCODE_ERROR.name());
            nm.setdata (message);
            senddata(nm);
        }
    }

/** закрываем dialogue <br>
    вызывает transferCleanup()
*/
    private void stopTalking (String consoleMessage) {
        if (consoleMessage != null)
            lnprint (consoleMessage);

        if (dialogue != null)
            dialogue.close();    dialogue = null;
    }

    private void informClientWithOperationCode (NasMsg nm, OperationCodes opcode) {
        if (socketChannel != null) {
            nm.setOpCode(opcode);
            senddata(nm);
        }
    }

/** Возможно, эти три метода нужно будет похерить, но это будет видно позже.  */
    @Override public void onChannelActive (@NotNull ChannelHandlerContext ctx) {

        LOGGER.info("onChannelActive(): открыто соединение: ctx: " + ctx);
        if (socketChannel == null) {
            socketChannel = (SocketChannel) ctx.channel();
        }
    }

    @Override public void onChannelInactive (@NotNull ChannelHandlerContext ctx) {

        LOGGER.info("onChannelInactive(): закрыто соединение: ctx: " + ctx);
        if (userName != null) {
            clientRemove(this, userName);
            userName = null;
        }
    }

    @Override public void onExceptionCaught (@NotNull ChannelHandlerContext ctx, @NotNull Throwable cause)
    {
        LOGGER.info("onExceptionCaught(): аварийное закрытие соединения: ctx: " + ctx);
        clientRemove (this, userName);
        userName = null;
    }
//----------------------------------- EXIT ----------------------------------------------------------------------*/

/** Этот метод вызывается на той стороне, на которой инициируется закрытие соединения.  */
    @Override public void startExitRequest () {
        inlineDoExit ("Сервер разрывает соединение с клиентом: "+ userName, true);
    }

/** От клиента пришло сообщене о том, что он собирается разорвать соединение.   */
    @ManipulateMethod (opcodes = NM_OPCODE_EXIT)
    private void manipulateExitRequest (NasMsg nm) {
        inlineDoExit ("Клиент разрывает соединение.\n", false);
    }

/** Выполняет:<br>
    discardCurrentOperation();<br>
    discardUser();<br>
    socketChannel.disconnect();<br>socketChannel.close(). */
    void inlineDoExit (String str, boolean notifyUser) {
        lnprint (str);
        if (socketChannel != null) {
            discardCurrentOperation();
            if (notifyUser) {
                NasMsg nm = new NasMsg (NM_OPCODE_EXIT, PROMPT_CONNECTION_GETTING_CLOSED, OUTBOUND);
                socketChannel.writeAndFlush (nm);
                LOGGER.debug ("Отправлено сообщение "+ NM_OPCODE_EXIT);
            }
            discardUser();
            socketChannel.disconnect();
            socketChannel.close();
        }
    }

    private void discardUser () {
        if (userName != null)
            clientRemove (this, userName);
        userName = null;
    }

/** вызывается ТОЛЬКО из обработчиков EXIT с целью прервать текущую операцию */
    private void discardCurrentOperation ()
    {
        if (dialogue != null) {
            OperationCodes theme = dialogue.getTheme();
            switch (theme) {
                case NM_OPCODE_LOAD2SERVER:
                    //dialogue.discardExtruding();
                case NM_OPCODE_LOAD2LOCAL:
                    replyWithErrorMessage (new NasMsg (theme, null, OUTBOUND), NM_OPCODE_EXIT.name());
                    break;
            }
            stopTalking (USE_DEFAULT_MSG);
        }
    }

/** Составляем список методов-обработчиков сообщений.<p>
Преподаватель сказал, что switch-case это не круто, требует доп.усилий при добавлении в код новых методов и
 нужно это дело как-то упростить, например, при пом.рефлексии и списка методов.<p>
 Это, конечно, правильно, но каждый
 раз при добавлении новых методов кто-то всё равно будет пробегать по коду, чтобы понять, как тут добавляются
 новые методы. А комментарии преподаватель добавлять не советует, ссылаясь на то, что они всё время
 устаревают… Что тут скажешь…  */
    private void buildMethodsMaps () {

        mapManipulateMetods = new HashMap<>();
        mapEndupMetods = new HashMap<>();
        Method[] methods = RemoteManipulator.class.getDeclaredMethods();

        for (Method m : methods) {
            if (m.isAnnotationPresent(ManipulateMethod.class)) {
                ManipulateMethod annotation = m.getAnnotation(ManipulateMethod.class);
                OperationCodes[] opcodes    = annotation.opcodes();
                for (OperationCodes code : opcodes) {
                    mapManipulateMetods.put(code, m);
                }
            }
            if (m.isAnnotationPresent(EndupMethod.class)) {
                EndupMethod      annotation = m.getAnnotation(EndupMethod.class);
                OperationCodes[] opcodes    = annotation.opcodes();
                for (OperationCodes code : opcodes) {
                    mapEndupMetods.put(code, m);
                }
            }
        }
    }

/** Отсылаем NasMsg, предварительно установив NasMsg.inbound в OUTBOUND. */
    private void senddata (NasMsg nm) {
        socketChannel.writeAndFlush (nm.setinbound (OUTBOUND));
    }

/** То же, что и метод {@code LocalManipulator.senddata()}, но перед отправкой удаляются данные из
    {@code NasMsg.data}, чтобы не нагружать зря канал связи.  */
    private void sendempty (NasMsg nm) {
        senddata (nm.setdata (STR_EMPTY));
    }

/** Этот метод делает то же, что и метод {@code LocalManipulator.senddata()}, но предназначен для передачи
в качестве параметра. */
    private Function<NasMsg, Void> funcSendNasMsg () {
        return new Function<NasMsg, Void>() {
            @Override public Void apply (NasMsg nasMsg) {
                senddata(nasMsg);
                return null;
            }
        };
    }
}
