package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.client.services.ClientManipulator;
import ru.gb.simplenas.common.annotations.EndupMethod;
import ru.gb.simplenas.common.annotations.ManipulateMethod;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.services.FileExtruder;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasDialogue;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static ru.gb.simplenas.client.CFactory.PROMPT_FORMAT_UPLOADERROR_SRCFILE_ACCESS;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.structs.NasDialogue.*;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

/**
 Описание протокола обмена данными между клиентом и сервером см. в комментарии к<br>
 {@link ru.gb.simplenas.server.services.impl.RemoteManipulator}.<p>

 Там же есть описание добавления обработчиков для новых типов сообщений.
 */
public class LocalManipulator implements ClientManipulator {

    private static final String ERROR_OLD_DIALOGUE_STILL_RUNNING =
        "Не могу начать новый диалог, — предыдущий ещё не закрыт.";
    private final static Logger LOGGER = LogManager.getLogger(LocalManipulator.class.getName());
    private final SocketChannel schannel;
    private       Map<OperationCodes, Method> mapManipulateMetods, mapEndupMetods;

    private NasCallback   callbackChannelActive = this::callbackDummy;  //< для последовательного процесса подключения
    private NasCallback   callbackMsgIncoming   = this::callbackDummy;    //< для доставки результатов запросов
    private NasCallback   callbackInfo          = this::callbackDummy;           //< для сообщений, которых никто не ждёт
    private NasDialogue   dialogue;


    public LocalManipulator (NasCallback callbackChannelActive,
                             NasCallback callbackMsgIncoming,
                             NasCallback callbackInfo, SocketChannel sC)
    {
        this.callbackChannelActive = callbackChannelActive;
        this.callbackMsgIncoming = callbackMsgIncoming;
        this.callbackInfo = callbackInfo;
        this.schannel = sC;
        buildMethodsMaps();
        LOGGER.debug("создан LocalManipulator");
    }
//-----------------------------------------------------------------------------------------

    void callbackDummy (Object... objects) {}

    @Override public void handle (ChannelHandlerContext ctx, NasMsg nm) throws IOException {

        if (nm != null && nm.inbound() == OUTBOUND) {
            nm.setinbound (INBOUND);
            OperationCodes opcode = nm.opCode();
            try {
                Method m = mapManipulateMetods.get (opcode);
                if (m == null)
                    throw new UnsupportedOperationException (String.format(
                        "Нет метода для кода операции: «%s».", opcode));
                m.invoke(this, nm);
            }
            catch (IllegalArgumentException | ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }

//---------------------------------- обработчики простых сообщений ----------------------------------------------*/
/** Обрабатываем сообщения, которые являются заключительными (терминальными): OK, ERROR.  */
    @ManipulateMethod (opcodes = {NM_OPCODE_OK, NM_OPCODE_ERROR})
    private void manipulateEndups (NasMsg nm) {
        if (dialogue != null) {
            try {
                Method m = mapEndupMetods.get (dialogue.getTheme());
                m.invoke (this, nm);
            }
            catch (IllegalArgumentException | ReflectiveOperationException e) {e.printStackTrace();}
        }
        else if (DEBUG) throw new RuntimeException();
             else provideErrorMessage (nm, "Некорректное завершение операции.");
    }

/** Метод для отправки простых запросов: требующих «односложного» ответа сервера и не требующих
 пересылки других данных кроме самого сообщения.    */
    @Override public boolean startSimpleRequest (NasMsg nm) {               //OUT
        boolean result = false;
        if (!isManipulatorBusy (nm))
        if (nm != null && schannel != null) {
            dialogue = NasDialogueForSimpleRequest (nm);
            schannel.writeAndFlush(nm);
            result = true;
        }
        return result;
    }

/** Метод для обработки простых входящих терминальных сообщений. Работает это так: во время операции
    обмена данными с сервером нам приходит от сервера сообщение с кодом, который однозначно можно
    интерпретировать как код заврешения операции обмена: …OK, …ERROR или что-то в этом же роде. Метод
    <b>manipulateEndups</b>, получив такой код, извлекает из dialog тему диалога и по ней определяет,
    какой метод должен обработать полученный код завершения операции обмена.<p>
    Этот сценарий необязательный: можно огранизовать завершение операции иначе, если на то есть
    необходимость. */
    @EndupMethod (opcodes = {NM_OPCODE_CREATE,     NM_OPCODE_RENAME, NM_OPCODE_FILEINFO,
                             NM_OPCODE_COUNTITEMS, NM_OPCODE_DELETE, NM_OPCODE_LOGIN})
    private void endupSimpleRequest (NasMsg nm) {                //IN
        if (dialogue != null) {
            lnprint("M:пришло сообщение: " + nm.opCode() + "; тема: " + dialogue.getTheme());
            //dialogue.add(nm);
            stopTalking(nm);
        }
        else if (DEBUG) throw new RuntimeException();
             else provideErrorMessage (nm, "Некорректное завершение операции.");
    }

//---------------------------------- LIST ------------------------------------------------------*/

    @Override public boolean startListRequest (NasMsg nm) {          //OUT
        boolean done = false;
        if (!isManipulatorBusy (nm))
        if (nm != null && schannel != null) {
            dialogue = NasDialogueForList (nm);
            schannel.writeAndFlush(nm);
            lnprint("M:отправлено сообщение: " + nm.opCode() + "\n");
            done = true;
        }
        return done;
    }

    @ManipulateMethod (opcodes = NM_OPCODE_LIST)                      //IN, IN, ..., IN
    private void manipulateListQueue (NasMsg nm) {
        print("l");
        List<FileInfo> list;
        if (dialogue != null  &&  (list = dialogue.infolist()) != null) {
            list.add (nm.fileInfo());
        }
        else {
            provideErrorMessage (nm, sformat("\nОшибка при выполнении операции %s.\n",
                                 NM_OPCODE_LIST));
            sendempty (nm);
            stopTalking (nm);
        }
    }

/** обработка завершения передачи списка содержимого удалённой папки     */
    @EndupMethod (opcodes = NM_OPCODE_LIST)
    private void endupListRequest (NasMsg nm) {                      //IN
        if (dialogue != null) {
            List<FileInfo> list = dialogue.infolist();
            if (list == null) {
                provideErrorMessage (nm, sformat("\nОшибка при выполнении операции %s.\n", NM_OPCODE_LIST));
                list = newInfolist();
            }
            nm.setdata (list); //< список, который кропотливо составлял manipulateInboundList().
            stopTalking(nm);
        }
        else if (DEBUG) throw new RuntimeException();
             else provideErrorMessage (nm, "Некорректное завершение операции NM_OPCODE_LIST.");
    }

//---------------------------------- LOAD2LOCAL ------------------------------------------------*/
/** Отправа серверу запроса на пересылку файла от сервера к клиенту. */
    @Override public boolean startLoad2LocalRequest (String toLocalFolder, NasMsg nm) {      //OUT
        boolean result = false;
        String  errMsg = ERROR_UNABLE_TO_PERFORM;

        if (dialogue != null) {
            errMsg += sformat(" %s, т.к. выполняется операция %s", nm.opCode(), dialogue.getTheme());
        }
        else if (nm != null && schannel != null && nm.fileInfo() != null) {
            String fileName = nm.fileInfo().getFileName();

            if (sayNoToEmptyStrings (toLocalFolder, fileName, nm.msg())) {
                Path path = Paths.get (toLocalFolder, fileName);

                if ((dialogue = NasDialogueForDownloading (nm, path)) != null) {
                    nm.setdata(null);  //< чтобы сервер правильно обработал сообщение
                    schannel.writeAndFlush (nm);
                    lnprint ("M:отправлено сообщение: " + nm.opCode() + "\n");
                    result = true;
                }
                else errMsg += sformat (" загрузка файла\n%s\n", path);
            }
        }
        if (!result)
            provideErrorMessage (nm, errMsg);
        return result;
    }

    @ManipulateMethod (opcodes = NM_OPCODE_LOAD2LOCAL)
    private void manipulateLoad2LocalQueue (NasMsg nm) throws IOException {                //IN, IN, ..., IN
        if (dialogue != null)
        {
        //сервер сообщает, что готов передавать файл (на этом шаге пока нет никаких
        // действий с нашей стороны; просто отвечаем тем же сообщением):
            if (nm.msg().equals (NM_OPCODE_READY.name())) {
                sendempty (nm);
                printf ("\nПринимаем файл <%s>\n", dialogue.tc.path);
            }
        //сервер передаёт часть файла (или целый короткий файл):
            else if (nm.msg().equals (NM_OPCODE_DATA.name())) {
                if (dialogue.tc.fileDownloadAndWrite (nm)) {
                    sendempty (nm);
                }
                else {
                    String msg = String.format ("Не удалось принять файл: <%s>.", dialogue.tc.path);
                    LOGGER.error (msg);
                    transferCleanup (nm, msg);
                }
            }
        //сервер рапортует о нормальном окончании передачи файла:
            else if (nm.msg().equals (NM_OPCODE_OK.name())) {
                if (dialogue.tc.endupDownloading (nm)) {
                    printf ("\nПринят файл <%s>.\n", dialogue.tc.path);
                    nm.setOpCode (NM_OPCODE_OK); //< (старый стиль) чтобы в контроллере увидели успех
                    transferCleanup (nm, USE_DEFAULT_MSG);
                }
                else {  //меняем код сообщения и вызываем самих себя
                    nm.setmsg (NM_OPCODE_ERROR.name());
                    manipulateLoad2LocalQueue (nm);
                }
            }
        //во время передачи произошла ошибка (nm.data содержит сообщение от сервера):
            else if (nm.msg().equals (NM_OPCODE_ERROR.name())) {
                String msg = (String) nm.data();
                if (msg == null)
                    msg = String.format ("Не удалось принять файл: <%s>.", dialogue.tc.path);
                LOGGER.error (msg);
                nm.setOpCode (NM_OPCODE_ERROR); //< (старый стиль) чтобы в контроллере увидели ошибку
                transferCleanup (nm, msg);
            }
        //нужно разорвать соединение, не дожидаясь окончания передачи файла (мы не
        //знаем, откуда оно пришло, поэтому дублируем его на сервер):
            else if (nm.msg().equals (NM_OPCODE_EXIT.name())) {
                sendempty (nm);
                transferCleanup (nm, "Передача файла прервана.");
            }
            else {
                String msg = String.format ("Неизвестный код операции: %s", nm.msg());
                transferCleanup (nm, msg);
                throw new UnsupportedOperationException (msg);
            }
        }
        else provideErrorMessage (nm, ERROR_UNABLE_TO_PERFORM);
    }

//---------------------------------- LOAD2SERVER -------------------------------------------------*/

/** Отправка серверу запроса на пересылку файла от клиента к серверу.<p>
    Отправляем серверу сообщение с кодом {@code NM_OPCODE_LOAD2SERVER}. Этот код будет
    присутствовать во всех сообщениях во время передачи файла. «Уточняющие» коды сообщений
    будут находится в NasMsg.msg.<p>
    В этом методе также создаётся объект InputStream, при помощи которого будет считываться
    файл, если сервер ответит готовностью принять файл.<p>
    На этом этапе <b>NasMsg.msg</b> содержит полное название папки на стороне сервера, в которую
    планируется поместить передаваемый файл. Значение <b>NasMsg.data</b> должно быть равным NULL.
*/
    @Override public boolean startLoad2ServerRequest (String fromLocalFolder, NasMsg nm) {   //OUT
        boolean result = false;
        String errMsg = ERROR_UNABLE_TO_PERFORM;

        if (dialogue != null) {
            errMsg += sformat (" %s, т.к. выполняется операция %s", nm.opCode(), dialogue.getTheme());
        }
        else if (nm != null && nm.fileInfo() != null && schannel != null) {

            Path path = Paths.get (fromLocalFolder, nm.fileInfo().getFileName())
                             .toAbsolutePath()
                             .normalize();

            if ((dialogue = NasDialogueForUploading (nm, path)) != null) {
                nm.setdata(null);  //< чтобы сервер правильно обработал сообщение
                schannel.writeAndFlush (nm);
                lnprint("M:отправлено сообщение: " + nm.opCode() + "\n");
                result = true;
            }
            else {  //оставим сообщение об ошибке в nm, чтобы вызывающий метод мог его прочитать:
                errMsg += sformat (PROMPT_FORMAT_UPLOADERROR_SRCFILE_ACCESS,
                                  fromLocalFolder, FILE_SEPARATOR, nm.fileInfo().getFileName());
            }
        }
        if (!result)
            provideErrorMessage (nm, errMsg);
        return result;
    }

/** В ответ на предложение передать файл от клиента к серверу, сервер ответил готовностью принять файл.<p>
    При передаче файла некоторые поля NasMsg и NasMsg.FileInfo имеют иное назначение:<br>
    • NasMsg.msg — содержит, если можно так выразится, код вложенной операции NM_OPCODE_READY или
    NM_OPCODE_DATA (основной код операции по-прежнему остаётся в NasMsg.opCode);<br>
    • NasMsg.data — содержит передаваемую часть файла;<br>
    • NasMsg.fileInfo.filesize — содержит размер передаваемой части файла;<br>
    • ;<br>
*/
    @ManipulateMethod (opcodes = NM_OPCODE_LOAD2SERVER)
    private void manipulateLoad2ServerQueue (NasMsg nm) throws IOException {
        if (dialogue != null)
        {
        // отдача первой/единственной части файла:
            if (nm.msg().equals (NM_OPCODE_READY.name())) {
                printf ("\nОтдаём файл <%s>\n", dialogue.tc.path);
                nm.setmsg (NM_OPCODE_DATA.name());
                dialogue.tc.prepareToUpload(nm);
                dialogue.tc.fileReadAndUpload (nm, funcSendNasMsg());
            }
        //отдача промежуточной/последней части файла:
            else if (nm.msg().equals (NM_OPCODE_DATA.name())) {
                if (dialogue.tc.rest > 0L)
                    dialogue.tc.fileReadAndUpload (nm, funcSendNasMsg());
                else
                if (dialogue.tc.rest == 0L) {
                    //сообщаем серверу, что мы благополучно завершили свою часть работы:
                    nm.setmsg (NM_OPCODE_OK.name());
                    sendempty (nm);
                }
                else {
                    transferCleanup (nm, null);
                    throw new RuntimeException (nm.msg());
                }
            }
        //сервер подтвердил успешное завершение передачи:
            else if (nm.msg().equals (NM_OPCODE_OK.name())) {
                printf ("\nОтдан файл <%s>\n", dialogue.tc.path);
                nm.setOpCode (NM_OPCODE_OK);    //< (старый стиль) чтобы в контроллере увидели успех
                transferCleanup (nm, USE_DEFAULT_MSG);
            }
        //во время передачи произошла ошибка (nm.data содержит сообщение от сервера):
            else if (nm.msg().equals (NM_OPCODE_ERROR.name())) {
                String msg = (String) nm.data();
                if (msg == null)
                    msg = String.format ("Не удалось отдать файл: <%s>.", dialogue.tc.path);
                LOGGER.error (msg);
                nm.setOpCode (NM_OPCODE_ERROR); //< (старый стиль) чтобы в контроллере увидели ошибку
                transferCleanup (nm, msg);
            }
        //нужно разорвать соединение, не дожидаясь окончания отдачи файла (мы не
        //знаем, откуда оно пришло, поэтому дублируем его на сервер):
            else if (nm.msg().equals (NM_OPCODE_EXIT.name())) {
                sendempty (nm);
                transferCleanup (nm, "Передача файла прервана.");
            }
        //неизвестный код сообщения
            else {
                String msg = String.format ("Неизвестный код операции: %s", nm.msg());
                transferCleanup (nm, msg);
                throw new UnsupportedOperationException (msg);
            }
        }
        else provideErrorMessage (nm, ERROR_UNABLE_TO_PERFORM);
    }

//--------------------------------- EXIT ------------------------------------------------------
/** NetClient прислал задачу отправить серверу сообщение EXIT. Завершаем текущую операцию и
    отсылаем EXIT серверу. */
    @Override public void startExitRequest () {     //OUT

        discardCurrentOperation ();  //< скорее всего это не понадобиться, т.к. GUI блокируется на время операции
        if (schannel != null) {
            NasMsg nm = new NasMsg(NM_OPCODE_EXIT, OUTBOUND);
            schannel.writeAndFlush(nm);
            lnprint("M:Отправлено сообщение " + OperationCodes.NM_OPCODE_EXIT);
        }
    }

    //прерываем текущую операцию. некоторым операциям может потребоваться особый способ прерывания.
    private void discardCurrentOperation () {
        if (dialogue != null)
        try {
            OperationCodes theme = dialogue.getTheme();
            if (theme == NM_OPCODE_LOAD2LOCAL) {
                dialogue.discardExtruding();
                NasMsg nm = new NasMsg (theme, NM_OPCODE_EXIT.name(), OUTBOUND);
                manipulateLoad2LocalQueue (nm);
            }
            else if (theme == NM_OPCODE_LOAD2SERVER) {
                NasMsg nm = new NasMsg (theme, NM_OPCODE_EXIT.name(), OUTBOUND);
                manipulateLoad2ServerQueue (nm);
            }
            //else {}
        }
        catch (IOException e) { e.printStackTrace(); }
    }

//сервер прислал EXIT. Прерываем текущую задачу и сообщаем юзеру об обрыве связи.
    @ManipulateMethod (opcodes = NM_OPCODE_EXIT)
    private void manipulateExitRequest (NasMsg nm) throws IOException {         //IN

        lnprint("M:Получено сообщение " + OperationCodes.NM_OPCODE_EXIT);
        discardCurrentOperation();
    /*  если от сервера EXIT пришёл во время выполнения запроса юзера, то opCode = ERROR заставит
    вызывающий метод стандартно обработать не(до)получение данных, а сообщение в nm.msg о разрыве
    соединения всё объяснит юзеру. */
        nm.setOpCode (NM_OPCODE_ERROR);
        stopTalking(nm);
    }

//---------------------------------- общение с InboundHandler'ом ---------------------------

//Возможно, эти три метода нужно будет похерить, но это будет видно позже; сейчас они нужны серверу для
    @Override public void onChannelActive (ChannelHandlerContext ctx) {

        lnprint("onChannelActive(): открыто соединение: cts: " + ctx.channel());
        callbackChannelActive.callback();   //< этого колбэка ждёт NetClient.login(), чтобы продолжить работу.
    }

    @Override public void onChannelInactive (ChannelHandlerContext ctx) {
        lnprint("onChannelInactive(): закрыто соединение: ctx: " + ctx);
    }

    @Override public void onExceptionCaught (ChannelHandlerContext ctx, Throwable cause) {
        lnprint("onExceptionCaught(): аварийное закрытие соединения: ctx: " + ctx);
    }

//---------------------------------- другие полезные методы --------------------------

/** Закрываем диалог и отправляем его копию в callbackMsgIncoming. */
    private void stopTalking (NasMsg nm) {
        NasDialogue dlg = dialogue;
        if (dialogue != null) {
            dialogue.close();
            dialogue = null;
        }
        else if (DEBUG) throw new RuntimeException();
             else provideErrorMessage (nm, "Некорректное завершение операции.");
        callbackMsgIncoming.callback (nm, dlg);
    }

/** Шлём серверу сообщение об ошибке. */
    public void replyWithErrorMessage () {
        if (dialogue != null && schannel != null) {
            NasMsg nm = new NasMsg (NM_OPCODE_ERROR, STR_EMPTY, OUTBOUND);
            //dialogue.add(nm);
            schannel.writeAndFlush(nm);
            lnprint("M:отправлено сообщение: " + nm.opCode() + "; тема: " + dialogue.getTheme());
        }
    }

/** Составление списков методов, чтобы использовать эти спики вместо switch-case'ов.  */
    private void buildMethodsMaps () {

        mapManipulateMetods = new HashMap<>();
        mapEndupMetods = new HashMap<>();
        Method[] methods = LocalManipulator.class.getDeclaredMethods();

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
        schannel.writeAndFlush (nm.setinbound (OUTBOUND));
    }

/** То же, что и метод {@code LocalManipulator.senddata()}, но перед отправкой удаляются
    данные из {@code NasMsg.data}, чтобы не нагружать зря канал связи.  */
    private void sendempty (NasMsg nm) {
        senddata (nm.setdata (STR_EMPTY));  //< чтобы сервер правильно обработал сообщение
    }

/** Этот метод делает то же, что и метод {@code LocalManipulator.send()}, но предназначен
    для передачи в качестве параметра. */
    private Function<NasMsg, Void> funcSendNasMsg () {
        return new Function<NasMsg, Void>() {
            @Override public Void apply (NasMsg nasMsg) {
                senddata(nasMsg);
                return null;
            }
        };
    }

/** делает {@code dialogue.add(nm)}<br>
    и помещает message в {@code NasMsg.msg} (для вызывающей ф-ции)<br>
    и вызывает {@code stopTalking(nm)} */
    private void transferCleanup (NasMsg nm, String message) {
        if (message != null)
            nm.setmsg (message);   //< это сообщение для вызывающей ф-ции
        stopTalking (nm);
    }

/** Проверяем, не занят ли клиент обработкой к-л. запроса и, если занят, составляем для юзера
 сообщение и помещаем его в <b>nm.msg</b>. */
    private boolean isManipulatorBusy (NasMsg nm) {
        boolean busy = dialogue != null;
        if (busy)
            provideErrorMessage (nm, sformat ("Клиент занят операцией: %s.", dialogue.getTheme()));
        return busy;
    }

/** Выводим сообщение об ошибке в {@code NasMsg.msg} или в консоль. Плюс
устанавливаем {@code NasMsg.opCode} в NM_OPCODE_ERROR. */
    private static void provideErrorMessage (NasMsg nm, String errMsg) {
        if (nm != null) {
            nm.setOpCode (NM_OPCODE_ERROR);
            nm.setmsg(errMsg);
        }
        else errprint(errMsg);
    }
}
