package ru.gb.simplenas.client;

import org.jetbrains.annotations.NotNull;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableView;
import ru.gb.simplenas.client.services.ClientPropertyManager;
import ru.gb.simplenas.client.services.ClientWatchService;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.client.services.impl.*;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.services.impl.NasFileManager;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.client.structs.TableFileInfo;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;

import static ru.gb.simplenas.common.CommonData.MAINWND_TITLE;


public class CFactory {
    public static final int
        DEFAULT_PORT_NUMBER = 8289,
        DEFAULT_FONT_SIZE = 9,
        MIN_FONT_SIZE = 9,
        MAX_FONT_SIZE = 30;

    public static final String
        STYLE_FORMAT_SET_FONT_SIZE = "-fx-font-size: %dpt",
        CLIENT_PROPERTY_FILE_NAME  = "local.properties",   //< файл настроек (property file)
        DEFAULT_HOST_NAME          = "localhost",
        PROPNAME_PORT = "PORT",
        PROPNAME_HOST = "HOST",
        PROPNAME_PATH_LOCAL = "MRU.PATH.LOCAL",
        PROPNAME_PATH_REMOTE = "MRU.PATH.REMOTE",
        PROPNAME_FONT_SIZE = "FONT.SIZE",

        STR_DEF_FOLDER = "user.dir", //< текущая папка юзера;   "user.home" < папка в учётной записи
        SBAR_TEXT_SERVER_NOCONNECTION = "Соединение с сервером отсутствует.",
        SBAR_TEXT_SERVER_ONAIR = "Есть подключение к серверу.",
        SBAR_TEXT_FOLDER_READING_IN_PROGRESS = "Выполняется чтение содержимого папки.",
        SBAR_TEXTFORMAT_STATISTICS = "%s  •••  %s",
        SBAR_TEXTFORMAT_FOLDER_STATISTICS = "%s папка содержит: %d папок, %d файлов.",
        STR_PREFIX_LOCAL = "Локальная",
        STR_PREFIX_REMOTE = "Удалённая",
        //String TEXTFIELD_SERVER_PROMPTTEXT_DOCONNECT = "Для подключения к серверу введите имя пользователя и нажмите ENTER.",
        //String TEXTFIELD_SERVER_PROMPTTEXT_LOGGEDIN = "Укажите папку на сервере, с которой хотите начать работать, и нажмите ENTER.",
        STRFORMAT_YOUARE_LOGGEDIN = "Вы успешно авторизованы как\n\n%s",
        ALERT_TITLE = MAINWND_TITLE,
        NO_USER_TITLE = "(?)",
        NEW_FOLDER_NAME = "Новая папка",

        ALERTHEADER_AUTHENTIFICATION = "Авторизация.",
        ALERTHEADER_CONNECTION       = "Подключение к серверу.",
        ALERTHEADER_REMOUTE_STORAGE  = "Удалённое хранилище.",
        ALERTHEADER_LOCAL_STORAGE    = "Локальное хранилище.",
        ALERTHEADER_LOCAL2SERVER = "Выгрузка файла на сервер.",
        ALERTHEADER_LOCAL2LOCAL  = "Загрузка файла с сервера.",
        ALERTHEADER_DOWNLOADING  = "Загрузка файла.",
        ALERTHEADER_UPLOADING    = "Выгрузка файла.",
        ALERTHEADER_DELETION = "Удаление.",
        ALERTHEADER_RENAMING = "Переименование.",
        ALERTHEADER_FOLDER_CREATION      = "Создание папки.",
        ALERTHEADER_UNABLE_APPLY_AS_PATH = "Не удалось перейти в указанную папку.",

        PROMPT_FORMAT_ROOT_FOLDER = "Текущая папка является корневой\n\n%s",
        PROMPT_FORMAT_REPLACE_CONFIRMATION = "Файл уже существует в выбранной папке. Хотите его перезаписать?\n\n%s\n",
        PROMPT_FORMAT_FOLDER_DELETION_CONFIRMATION = "Удаляемая папка НЕ пуста. Всё равно удалить её?\n\n%s\n",
        PROMPT_FORMAT_FILE_DELETION_CONFIRMATION = "Подтвердите удаление файла:\n\n%s\n",
        //PROMPT_FORMAT_RENAMING_ALREADY_EXISTS = "Переименование отклонено, — папка или файл с таким имененм уже существуют:\n\n%s\n",
        PROMPT_FORMAT_UPLOADERROR_SRCFILE_ACCESS = " т.к. не удалось получить доступ к фалу:\n%s%s%s\n",
        PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED = "Пересылка папок не поддерживается.",
        PROMPT_FILE_IS_NOT_ACCESSIBLE = "Доступ к файлу закрыт.",
        PROMPT_CONFIRM_CREATE_FOLDER = "Введите имя для новой папки и нажмите ENTER.",
        PROMPT_DIR_ENTRY_DOESNOT_EXISTS = "Папка или файл не существуют!",
        ERROR_UNABLE_CONNECT_TO_SERVER = "Не удалось подключиться к серверу.",
        ERROR_USUPPORTED_OPERATION_REQUESTED = "Запрошена неподдерживаемая операция.",
        ERROR_ABNORMAL_APPLICATION_BEHAVIOUR = "Abnormal application behaviour.",
        ERROR_UNABLE_GET_LIST = "Не удалось получить список содержимого папки.",
        ERROR_NO_CONNECTION_TO_REMOTE_STORAGE = "Нет подключения к серверу.",
        PROMPT_FORMAT_UNABLE_LIST = "Не удалось вывести список содержимого папки.\n\n%s\n",
        PROMPT_INVALID_USER_NAME = "Указано недопустимое имя пользователя. Имя пользователя должно содержать только буквы и цифры.",
        PROMPT_INVALID_PASSWORD = "Указан недопустимый пароль.";
        //PROMPT_FORMAT_UNABLE_APPLY_PATH = "Не удалось вывести список содержимого папки.\n\n%s\n";


    private CFactory () {}

    static NetClient newNetClient (NasCallback cbDisconnection, int port, String hostName) {
        return new LocalNetClient(cbDisconnection, port, hostName);
    }

    static ClientWatchService getWatchServiceInstance () { return LocalWatchService.getInstance(); }

//-------------------------- методы для работы с контекстным меню -----------------------------------------------*/

/*    public static void setContextMenuEventHandler_OnShoing (ContextMenu menu, TableView<TableFileInfo> tv)
    {
        ContextMenuManager.setContextMenuEventHandler_OnShoing (menu, tv);
    }*/
//-------------------------- методы для работы с TableView ------------------------------------------------------*/

    public static Point populateTv (@NotNull TableView<TableFileInfo> tv, @NotNull List<FileInfo> infolist) {
        return TableViewManager.populateTv (tv, infolist);
    }

    public static void clearTv (@NotNull TableView<TableFileInfo> tv) {
        TableViewManager.clear (tv);
    }

    public static Point statisticsTv (@NotNull TableView<TableFileInfo> tv) {
        return TableViewManager.statisticsTv(tv);
    }

    public static TableFileInfo addItemAsFolder (@NotNull String name, @NotNull TableView<TableFileInfo> tv) {
        return TableViewManager.addItemAsFolder(name, tv);
    }

    public static void deleteTvItem (TableView<TableFileInfo> tv, TableFileInfo t) {
        TableViewManager.deleteTvItem(tv, t);
    }

//--------------------------------- ClientPropertyManager --------------------------------------------------------*/

    public static ClientPropertyManager getProperyManager () {
        return LocalPropertyManager.getInstance();
    }
}
