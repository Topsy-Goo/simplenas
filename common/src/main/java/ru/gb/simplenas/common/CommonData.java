package ru.gb.simplenas.common;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CommonData {
    public static final boolean DEBUG = true;
    public static final int PORT = 8289;
    public static final String SERVER_ADDRESS = "localhost";

    public static final TimeUnit filetimeUnits = TimeUnit.SECONDS;
    public static final String FILETIME_FORMAT_PATTERN = " yyyy-MM-dd | HH:mm:ss ";

    public static final long FILESIZE_KILOBYTE = 1024L;
    public static final String MAINWND_TITLE = "Simple.NAS client";
    public static final String strFileSeparator = FileSystems.getDefault().getSeparator();
    public static final String STR_EMPTY = "";
    public static final Locale RU_LOCALE = new Locale("ru", "RU");
    public static final String ERROR_UNABLE_TO_PERFORM = "Не удалось выполнить операцию!";
    public static final String PROMPT_CONNECTION_GETTING_CLOSED = "Сервер разорвал соединение.";
    public static final String FOLDER_MARK = "DIR";

    public static final String STRPATH_CLOUD = "cloud";
    public static final Path CLOUD = Paths.get(STRPATH_CLOUD).toAbsolutePath();

    public static final List<String> INITIAL_FILES;
    public static final List<String> INITIAL_FOLDERS;
    public static final int MAX_BUFFER_SIZE = 1024 * 1024 - 512;

    static {
        INITIAL_FILES = new ArrayList<>();
        INITIAL_FILES.add("readme.txt");
        INITIAL_FILES.add("welcome.txt");
        INITIAL_FOLDERS = new ArrayList<>();
        INITIAL_FOLDERS.add("documentes");
        INITIAL_FOLDERS.add("pictures");
    }

}
