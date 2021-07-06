package ru.gb.simplenas.client;

import com.sun.istack.internal.NotNull;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableView;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.client.services.impl.ContextMenuManager;
import ru.gb.simplenas.client.services.impl.NasNetClient;
import ru.gb.simplenas.client.services.impl.TableViewManager;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.TableFileInfo;

import java.awt.*;
import java.util.List;

import static ru.gb.simplenas.common.CommonData.MAINWND_TITLE;


public class CFactory {
    public static final String SBAR_TEXT_SERVER_NOCONNECTION = "Соединение с сервером отсутствует.";
    public static final String SBAR_TEXT_SERVER_ONAIR = "Есть подключение к серверу.";
    public static final String SBAR_TEXT_FOLDER_READING_IN_PROGRESS = "Выполняется чтение содержимого папки.";
    public static final String SBAR_TEXTFORMAT_STATISTICS = "%s  •••  %s";
    public static final String SBAR_TEXTFORMAT_FOLDER_STATISTICS = "Папка содержит: %d папок, %d файлов.";
    public static final String TEXTFIELD_SERVER_PROMPTTEXT_DOCONNECT = "Для подключения к серверу введите имя пользователя и нажмите ENTER.";
    public static final String TEXTFIELD_SERVER_PROMPTTEXT_LOGGEDIN = "Укажите папку на сервере, с которой хотите начать работать, и нажмите ENTER.";
    public static final String STRFORMAT_YOUARE_LOGGEDIN = "Вы успешно авторизованы как\n\n%s";
    public static final String ALERT_TITLE = MAINWND_TITLE;
    public static final String NO_USER_TITLE = "(?)";
    public static final String NEW_FOLDER_NAME = "Новая папка";
    public static final String ALERTHEADER_AUTHENTIFICATION = "Авторизация.";
    public static final String ALERTHEADER_CONNECTION = "Подключение к серверу.";
    public static final String ALERTHEADER_REMOUTE_STORAGE = "Удалённое хранилище.";
    public static final String ALERTHEADER_LOCAL_STORAGE = "Локальное хранилище.";
    public static final String ALERTHEADER_RENAMING = "Переименование отклонено, — папка или файл с таким имененм уже существуют.";
    public static final String ALERTHEADER_DOWNLOADING = "Загрузка файла.";
    public static final String ALERTHEADER_UPLOADING = "Выгрузка файла.";
    public static final String ALERTHEADER_DELETION = "Удаление.";
    public static final String PROMPT_FORMAT_ROOT_FOLDER = "Текущая папка является корневой\n%s";
    public static final String PROMPT_CONFIRM_CREATE_FOLDER = "Введите имя для новой папки и нажмите ENTER.";
    public static final String PROMPT_FORMAT_REPLACE_CONFIRMATION = "Файл уже существует в выбранной папке. Хотите его перезаписать?\n%s";
    public static final String PROMPT_FORMAT_FOLDER_DELETION_CONFIRMATION = "Удаляемая папка НЕ пуста. Всё равно удалить её?\n%s";
    public static final String PROMPT_FORMAT_FILE_DELETION_CONFIRMATION = "Подтвердите удаление файла:\n\n%s";
    public static final String PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED = "Пересылка папок не поддерживается.";
    public static final String PROMPT_DIR_ENTRY_DOESNOT_EXISTS = "Папка или файл не существуют!";
    public static final String STR_DEF_FOLDER = "user.dir";
    public static final String ERROR_UNABLE_CONNECT_TO_SERVER = "Не удалось подключиться к серверу.";
    public static final String ERROR_USUPPORTED_OPERATION_REQUESTED = "Запрошена неподдерживаемая операция.";
    public static final String ERROR_ABNORMAL_APPLICATION_BEHAVIOUR = "Abnormal application behaviour.";
    public static final String ERROR_UNABLE_GET_LIST = "Не удалось получить список содержимого папки.";
    public static final String ERROR_NO_CONNECTION_TO_REMOTE_STORAGE = "Нет подключения к серверу.";


    private CFactory () {}

    static NetClient newNetClient (NasCallback cbDisconnection) { return new NasNetClient(cbDisconnection); }

    public static void setContextMenuEventHandler_OnShoing (ContextMenu menu, TableView<TableFileInfo> tv) {
        ContextMenuManager.setContextMenuEventHandler_OnShoing(menu, tv);
    }

    public static Point populateTv (@NotNull TableView<TableFileInfo> tv, @NotNull List<FileInfo> infolist) {
        return TableViewManager.populateTv(tv, infolist);
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

}
