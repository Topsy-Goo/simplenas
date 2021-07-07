package ru.gb.simplenas.common;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CommonData
{
    public static final boolean DEBUG =      true;
    public static final boolean INBOUND =    true, OUTBOUND =      false;
    public static final boolean LOCAL =      true, REMOTE =        false;
    public static final boolean ANSWER_OK =  true, ANSWER_CANCEL = false;
    //public static final boolean ANSWER_YES = true, ANSWER_NO =     false;
    public static final boolean FOLDER =     true, NOT_FOLDER =    false;
    public static final boolean EXISTS =     true, NOT_EXISTS =    false;
    public static final boolean SYMBOLIC =   true, NOT_SYMBOLIC =  false;
    public static final boolean ENABLE =     true, DISABLE =       false;

    public static final int PORT = 8289;
    public static final String SERVER_ADDRESS = "localhost";

    public static final TimeUnit filetimeUnits = TimeUnit.SECONDS;
    public static final String FILETIME_FORMAT_PATTERN = " yyyy-MM-dd | HH:mm:ss ";

    public static final long FILESIZE_KILOBYTE = 1024L;
    public static final String MAINWND_TITLE = "Simple.NAS client";
    public static final String strFileSeparator = FileSystems.getDefault().getSeparator();
    public static final String STR_EMPTY = "";
    public static final Locale RU_LOCALE = new Locale ("ru", "RU");
    public static final String ERROR_UNABLE_TO_PERFORM = "Не удалось выполнить операцию!";
    public static final String PROMPT_CONNECTION_GETTING_CLOSED = "Сервер разорвал соединение.";
    public static final String FOLDER_MARK = "DIR";

    public static final String STR_CLOUD = "cloud";
    public static final Path CLOUD = Paths.get(STR_CLOUD).toAbsolutePath();
    public static final int MAX_USERNAME_LENGTH = 32;

    public static final List<String> INITIAL_FILES; //< файлы, которые должны быть в папке у нового пользователя.
    public static final List<String> INITIAL_FOLDERS; //< папки, которые должны быть в папке у нового пользователя.
    public static final int INT_MAX_OBJECT_SIZE = 1024 * 1024 * 100 +512;
    public static final int INT_MAX_BUFFER_SIZE = INT_MAX_OBJECT_SIZE; //< макс.размер буфера в байтах (сначала размер был
    // 1024*1024, ногда буфер был присоединён к NasMsg.data, то при попытке передать его в канал Java выдала исключение,
    // сысл которого сводился к тому, что 319 байтов лишние.)
    public static final String WF_ = ".wf:";
    public static final String RF_ = ".rf:";

    static
    {
        INITIAL_FILES = new ArrayList<>();
        INITIAL_FILES.add("readme.txt");
        INITIAL_FILES.add("welcome.txt");
        INITIAL_FOLDERS = new ArrayList<>();
        INITIAL_FOLDERS.add("documentes");
        INITIAL_FOLDERS.add("pictures");
    }

}
