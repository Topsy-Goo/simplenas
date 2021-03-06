package ru.gb.simplenas.common;

import java.nio.file.FileSystems;
import java.time.ZoneId;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CommonData {

    public static final boolean DEBUG =      true;
    public static final boolean INBOUND =    true, OUTBOUND =      !INBOUND;
    public static final boolean LOCAL =      true, REMOTE =        !LOCAL;
    public static final boolean ANSWER_OK =  true, ANSWER_CANCEL = !ANSWER_OK;
    //public static final boolean ANSWER_YES = true, ANSWER_NO =     false;
    public static final boolean FOLDER =     true, NOT_FOLDER =    !FOLDER;
    public static final boolean EXISTS =     true, NOT_EXISTS =    !EXISTS;
    public static final boolean SYMBOLIC =   true, NOT_SYMBOLIC =  !SYMBOLIC;
    public static final boolean ENABLE =     true, DISABLE =       !ENABLE;
    public static final boolean CONNECTED =  true, NOT_CONNECTED = !CONNECTED;
    public  static final boolean FAIR      = true;

    public static final ZoneId   ZONE_ID = ZoneId.systemDefault();
    public static final Locale   RU_LOCALE      = new Locale ("ru", "RU");
    public static final String   FILE_SEPARATOR = FileSystems.getDefault().getSeparator();
    public static final TimeUnit FILETIME_UNITS = TimeUnit.SECONDS;
    public static final String   FILETIME_FORMAT_PATTERN = " yyyy-MM-dd | HH:mm:ss ";

    public static final long FILESIZE_KILOBYTE = 1024L;
    public static final String
        PROPFILE_COMMENT = "Этот файл настроек сгенерирован/обновлён автоматически.",
        MAINWND_TITLE    = "Simple.NAS client",
        STR_EMPTY        = "",
        USE_DEFAULT_MSG  = null,
        ERROR_UNABLE_TO_PERFORM          = "Не удалось выполнить операцию!",
        PROMPT_CONNECTION_GETTING_CLOSED = "Сервер разорвал соединение.",
        PROMPT_INCORRECT_OPERATION_TERMINATION = "Некорректное завершение операции.",
        FOLDER_MARK = "DIR"
        ;

    public static final int MAX_USERNAME_LENGTH = 32;
    public static final long NO_SIZE_VALUE = -1L;

    public static final int INT_MAX_OBJECT_SIZE = 1024 * 1024 * 100/* +512*/;
    public static final int INT_MAX_BUFFER_SIZE = INT_MAX_OBJECT_SIZE; //< макс.размер буфера в байтах (сначала размер был
    // 1024*1024, ногда буфер был присоединён к NasMsg.data, то при попытке передать его в канал Java выдала исключение,
    // сысл которого сводился к тому, что 319 байтов лишние.)
    public static final String WF_ = " .wf:";
    public static final String RF_ = " .rf:";
}
