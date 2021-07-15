package ru.gb.simplenas.client;

import com.sun.istack.internal.NotNull;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.client.services.ClientPropertyManager;
import ru.gb.simplenas.client.services.ClientWatchService;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.client.services.impl.LocalWatchService;
import ru.gb.simplenas.client.services.impl.TableViewManager;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;
import ru.gb.simplenas.client.structs.TableFileInfo;

import java.awt.*;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javafx.scene.control.Alert.AlertType.*;
import static javafx.scene.control.Alert.AlertType.ERROR;
import static ru.gb.simplenas.client.CFactory.*;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.services.impl.NasFileManager.getParentFromRelative;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class Controller implements Initializable
{
    @FXML public VBox rootbox;
    @FXML public Text textStatusBar;
    @FXML public TextField textfieldCurrentPath_Client;
    @FXML public TextField textfieldCurrentPath_Server;
    @FXML public Button buttonDownload;
    @FXML public Button buttonUpload;
    @FXML public Button buttonLevelUp_Client;
    @FXML public Button buttonLevelUp_Server;
    @FXML public ContextMenu menuClientTableActions;
    @FXML public ContextMenu menuServerTableActions;
    @FXML public TableView<TableFileInfo> tvClientSide;
    @FXML public TableView<TableFileInfo>  tvServerSide;
    @FXML public TableColumn<TableFileInfo, String> columnClientFolderMark;
    @FXML public TableColumn<TableFileInfo, String> columnClientFileName;
    @FXML public TableColumn<TableFileInfo, String> columnClientFileSize;
    @FXML public TableColumn<TableFileInfo, String> columnClientModified;
    @FXML public TableColumn<TableFileInfo, String> columnClientCreated;
    @FXML public TableColumn<TableFileInfo, String> columnServerFolderMark;
    @FXML public TableColumn<TableFileInfo, String> columnServerFileName;
    @FXML public TableColumn<TableFileInfo, String> columnServerFileSize;
    @FXML public TableColumn<TableFileInfo, String> columnServerModified;
    @FXML public TableColumn<TableFileInfo, String> columnServerCreated;

    private Stage primaryStage;
    private NetClient netClient;
    private String userName;
    private String strCurrentLocalPath = STR_EMPTY; // не имеет отношения к textfieldCurrentPath_Client
    private String strCurrentServerPath = STR_EMPTY;// не имеет отношения к textfieldCurrentPath_Server
    private String sbarTextDefault = STR_EMPTY;
    private String sbarLocalStatistics = STR_EMPTY;
    private String sbarServerStatistics = STR_EMPTY;

    private Thread javafx;
    private final Object syncObj = new Object();
    private boolean extraInitialisationIsDone = false;
    private TableViewManager tableViewManager;
    private ClientPropertyManager propMan;
    private ClientWatchService clientWatcher;
    private Lock lockOnWatching;
    private static final Logger LOGGER = LogManager.getLogger(Controller.class.getName());


//TODO : авторизация + БД + флайвэй
//TODO : DnD
//TODO : Грэдл
//
//TODO : Все файлы больше 3`331`795`456 байтов скачиваются именно в таком размере.
//          Files.newOutputStream().write (byte[size]) <-----NasMsg.data = byte[size]------- Files.newInputStream(path, READ).read (byte[size])
//       Использование на стороне сервера
//          Files.newByteChannel().write (ByteBuffer.wrap(byte[]))
//       привело к тому, что ни один файл не передался с правильным размером, но зато размер перестал быть проблемой. Пришлось
//       вернуться к Files.newOutputStream.
//          Позже было установлено, что своевременное использование OutputStream.flush() отчасти решает проблему:
//       теперь, чтобы скачать большой файл А, нужно сперва скачать файл Б большего размера, чем файл А, и
//       посмотреть, какая часть файла Б скачалась. Если размер скачанной части больше или равен размеру файла А,
//       то файл А можно спокойно скачивать, — ресурсы под него у JVM уже есть. Если размер скачанной части
//       файла Б меньше размера файла А, то нужно повторить процедуру, но вместо файла Б взять файл побольше.
//          Скажем, мы скачали файл размером 6 049 128 413 байтов, из которых скачались только 5 099 879 424.
//       Следовательно, если мы станем скачивать файл размером 4 373 616 852 байтов, то он скачается полностью.
//          В общем суть рассказа в том, что система для JVM выделяет не всю затребованную память, а какую-то часть,
//       но если уж выделила, то этой выделенной памятью можно пользоваться не боясь.
//
//TODO : Глюк: неудачная попытка входа в запрещённые папки ломает связь текущей папки и строки пути.

//------------------------------- инициализация и завершение ----------------------------------------------------*/

    @Override public void initialize (URL location, ResourceBundle resources)
    {
        javafx = Thread.currentThread();

        propMan = getProperyManager();
        strCurrentLocalPath = propMan.getLastLocalPathString();

        lockOnWatching = new ReentrantLock();
        clientWatcher = new LocalWatchService ((ooo)->callbackOnCurrentFolderEvents(), lockOnWatching);
        clientWatcher.startWatchingOnFolder(strCurrentLocalPath);

        sbarSetDefaultText (STR_EMPTY, SBAR_TEXT_SERVER_NOCONNECTION);

        tableViewManager = new TableViewManager (this);
        textfieldCurrentPath_Client.setText (strCurrentLocalPath);
        textfieldCurrentPath_Server.setPromptText(TEXTFIELD_SERVER_PROMPTTEXT_DOCONNECT);
        populateTableView (listFolderContents (strCurrentLocalPath), LOCAL);

        setContextMenuEventHandler_OnShoing (menuClientTableActions, tvClientSide);
        setContextMenuEventHandler_OnShoing (menuServerTableActions, tvServerSide);

        extraInitialisationIsDone = false;
        enableUsersInput (ENABLE);
    }

//---------------------------------------------------------------------------------------------------------------*/

    void onCmdConnectAndLogin (String name)
    {
        if (sayNoToEmptyStrings(name))
        {
            //инициализация, которую нельзя было сделать в initialize()
            if (!extraInitialisationIsDone)
            {   //rootbox.getScene().getWindow().setOnCloseRequest((event)->closeSession()); < сделали это в MainGUI
                extraInitialisationIsDone = true;
            }
            if (connect())
            {
                login (name);
            }
            else messageBox(CFactory.ALERTHEADER_CONNECTION, ERROR_UNABLE_CONNECT_TO_SERVER, WARNING);
        }
        //LOGGER.trace("onCmdConnectAndLogin() end");
    }

    private boolean connect()
    {
        boolean result = false;
        if (propMan != null)
        {
            netClient = newNetClient(
                    (ooo)->callbackOnNetClientDisconnection(),
                    propMan.getRemotePort(),
                    propMan.getHostString());
            result = netClient.connect();
            if (!result)
            {
                netClient = null;
            }
        }
        return result;
    }

    private void login (String name)
    {
        NasMsg nm = null;
        if (netClient == null || (null == (nm = netClient.login (name))))
        {
            messageBox (ALERTHEADER_AUTHENTIFICATION, ERROR_UNABLE_TO_PERFORM, ERROR);
        }
        else if (nm.opCode() == OK)
        {
            userName = name;
            updateControlsOnSuccessfulLogin();
        }
        else if (nm.opCode() == OperationCodes.ERROR)
        {
            String strErr = nm.msg();
            if (!sayNoToEmptyStrings (strErr))
            {
                strErr = ERROR_UNABLE_TO_PERFORM;
            }
            messageBox (ALERTHEADER_AUTHENTIFICATION, strErr, WARNING);
        }
    }

    private void updateControlsOnSuccessfulLogin()
    {
        textfieldCurrentPath_Server.clear();
        textfieldCurrentPath_Server.setText (STR_EMPTY);
        textfieldCurrentPath_Server.setPromptText (TEXTFIELD_SERVER_PROMPTTEXT_LOGGEDIN);
        sbarSetDefaultText (null, SBAR_TEXT_SERVER_ONAIR);

        messageBox (ALERTHEADER_AUTHENTIFICATION,
                    String.format(STRFORMAT_YOUARE_LOGGEDIN, userName),
                    INFORMATION);

        if (propMan != null)
            strCurrentServerPath = propMan.getLastRemotePathString();

        String strRemotePath = Paths.get(userName, strCurrentServerPath).toString();
        if (!workUpAListRequestResult (netClient.list (strRemotePath)))
        {
            messageBox (ALERTHEADER_REMOUTE_STORAGE,
                        String.format(PROMPT_FORMAT_UNABLE_LIST, userName),
                        ERROR);
        }
        updateMainWndTitleWithUserName();
    }

// Наш обработчик события primaryStage.setOnCloseRequest.
    void closeSession()
    {
        storeProperties();  //< это нужно сделать до того, как userName станет == null
        if (netClient != null)
        {   //это приведёт к разрыву соединения с сервером (к закрытию канала) и к вызову onNetClientDisconnection()
            netClient.disconnect();
        }
        if (clientWatcher != null)
        {
            clientWatcher.close();
        }
    }

    private void storeProperties()
    {
        if (propMan != null)
        {
            propMan.setLastLocalPathString (strCurrentLocalPath);
            if (sayNoToEmptyStrings (strCurrentServerPath))  //< чтобы не сбрасывалось, если во время сессии не было подключения
            {
                propMan.setLastRemotePathString (relativizeByFolderName (userName, strCurrentServerPath));
            }
            propMan.close();
        }
    }

    void onMainWndShowing (Stage primaryStage) //< не надо делать этот метод private!
    {
        this.primaryStage = primaryStage;
    }

//callback. Вызывается из netClient при закрытии соединения с сервером. Может вызываться дважды для закрытия одной и той же сессии.
    private void callbackOnNetClientDisconnection()
    {
        netClient = null;
        userName = null;
        //Platform.runLater(()->{
        //    messageBox(CFactory.ALERTHEADER_REMOUTE_STORAGE, PROMPT_CONNECTION_GETTING_CLOSED, WARNING);  //< вызов не из потока javafx вызывает исключение
        //                      });
        sbarSetDefaultText(null, CFactory.SBAR_TEXT_SERVER_NOCONNECTION);
    }

    private void callbackOnCurrentFolderEvents()
    {
        applyStringAsNewLocalPath (strCurrentLocalPath);
    }

    private void updateMainWndTitleWithUserName()
    {
        if (primaryStage != null)
        {
            String newtitle = String.format("%s - вы вошли как : %s", MAINWND_TITLE, sayNoToEmptyStrings(userName) ? userName : CFactory.NO_USER_TITLE);
            primaryStage.setTitle (newtitle);
        }
    }

//------------------------------------------ обработчики команд GUI ---------------------------------------------*/

//Создание папки на сервере в текущей папке.
    @FXML public void onactionMenuServer_CreateFolder (ActionEvent actionEvent)
    {
        String name = generateDefaultFolderName (tvServerSide);
        String errMsg = null;
        NasMsg nasMsg = netClient.create (name);

        if (nasMsg == null)
        {
            errMsg = ERROR_UNABLE_TO_PERFORM;
        }
        else if (nasMsg.opCode() == OperationCodes.ERROR)
        {
            errMsg = nasMsg.msg();
        }
        else if (nasMsg.opCode() == OK)
        {
            addTvItemAsFolder(name, REMOTE);
        }
        if (errMsg != null)   messageBox (ALERTHEADER_FOLDER_CREATION, errMsg, ERROR);
    }

//Создание локальной папки в текущей папке.
    @FXML public void onactionMenuClient_CreateFolder (ActionEvent actionEvent)
    {
        String name = generateDefaultFolderName (tvClientSide);
        Path pSubfolder = createSubfolder (Paths.get(strCurrentLocalPath), name);

        if (null == pSubfolder)
            messageBox (ALERTHEADER_FOLDER_CREATION, ERROR_UNABLE_TO_PERFORM, ERROR);
    }

//в текущей клиентской папке удаляем выбранный элемент (файл или папку).
    @FXML public void onactionMenuClient_Delete (ActionEvent actionEvent)
    {
        TableFileInfo tfi = tvClientSide.getSelectionModel().getSelectedItem();
        String errMsg = null;

        if (tfi != null)
            deleteLocalEntryByTfi (tfi);
        else
            messageBox (ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR);
    }

//в текущей серверной папке удаляем выбранный элемент (файл или папку).
    @FXML public void onactionMenuServer_Delete (ActionEvent actionEvent)
    {
        TableFileInfo tfi = tvServerSide.getSelectionModel().getSelectedItem();
        String errMsg = null;

        if (netClient == null)
        {
            errMsg = CFactory.ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
        }
        else if (null == tfi)
        {
            errMsg = ERROR_UNABLE_TO_PERFORM;
        }
        else if (deleteRemoteEntryByIfi (tfi))
        {
            deleteTvItem (tvServerSide, tfi);
            collectStatistics (tvServerSide);
        }
        if (errMsg != null)  messageBox (ALERTHEADER_DELETION, errMsg, WARNING);

    }

//Загрузка одного выбранного элемента-файла с сервера в текущую локальную папку
    @FXML public void onactionMenu_Download (ActionEvent actionEvent)
    {
        TableFileInfo tfi;
        String errMsg = null;
        Alert.AlertType alertType = ERROR;

        if (netClient == null)
        {
            errMsg = CFactory.ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
            alertType = WARNING;
        }
        else if (null != (tfi = tvServerSide.getSelectionModel().getSelectedItem()))
        {
            //загрузка папок (даже пустых) не реализована
            if (tfi.getFolder())
            {
                errMsg = PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED;
                alertType = INFORMATION;
            }
            else errMsg = downloadFileByTfi (tfi);
        }
        if (errMsg != null)  messageBox (ALERTHEADER_LOCAL2LOCAL, errMsg, alertType);
    }

//Выгрузка одного выбранного элемента-файла из локальной папки на сервер в текущую папку
    @FXML public void onactionMenuClient_Upload (ActionEvent actionEvent)
    {
        TableFileInfo tfi;
        String errMsg = null;
        Alert.AlertType alertType = ERROR;

        if (netClient == null)
        {
            errMsg = ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
            alertType = WARNING;
        }
        else if (null != (tfi = tvClientSide.getSelectionModel().getSelectedItem()))
        {
            //загрузка папок (даже пустых) не реализована
            if (tfi.getFolder())
            {
                errMsg = PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED;
                alertType = INFORMATION;
            }
            else errMsg = uploadFileByTfi (tfi);
        }
        if (errMsg != null)   messageBox (ALERTHEADER_LOCAL2SERVER, errMsg, alertType);
    }

//В поле ввода textfieldCurrentPath_Client нажат ENTER.
    @FXML public void onactionTextField_Client_ApplyAsPath (ActionEvent actionEvent)
    {
        lockOnWatching.lock();
        {
            applyStringAsNewLocalPath (textfieldCurrentPath_Client.getText());
            clientWatcher.startWatchingOnFolder (strCurrentLocalPath);
        }
        lockOnWatching.unlock();
    }

//В поле ввода textfieldCurrentPath_Server нажат ENTER.
    @FXML public void onactionTextField_Server_ApplyAsPath (ActionEvent actionEvent)
    {
        String text = textfieldCurrentPath_Server.getText().trim();

        if (netClient == null)
        {   //перед подключением поле пути для сервера используется как поле ввода для имени пользователя
            onCmdConnectAndLogin(text);
        }
        else
        {   //если подключение уже установлено, то текст явялется папкой на сервере, которую нужно выбрать
            NasMsg nm = netClient.goTo (text);
            if (!workUpAListRequestResult (nm))
            {
                messageBox(ALERTHEADER_UNABLE_APPLY_AS_PATH, text, ERROR);
            }
        }
    }

//Переходим в родительскую папку, если таковая существует.
    @FXML public void onactionButton_Client_LevelUp (ActionEvent actionEvent)
    {
        // !!! Игнорируем содержимое поля ввода textfieldCurrentPath_Client)
        String strParent = stringPath2StringAbsoluteParentPath (strCurrentLocalPath);

        if (strParent != null)
        if (strParent.isEmpty())
        {
            messageBox (ALERTHEADER_LOCAL_STORAGE,
                        sformat (PROMPT_FORMAT_ROOT_FOLDER, strCurrentLocalPath),
                        INFORMATION);
        }
        else if (tryLock (1000, MILLISECONDS))
        {
            populateTableView (listFolderContents (strParent), LOCAL);
            strCurrentLocalPath = strParent;
            textfieldCurrentPath_Client.setText(strCurrentLocalPath);
            clientWatcher.startWatchingOnFolder (strCurrentLocalPath);
            lockOnWatching.unlock();
        }
    }

//Переходим в родительскую папку, если таковая существует.
    @FXML public void onactionButton_Server_LevelUp (ActionEvent actionEvent)
    {
        if (netClient != null)
        {
            String strParent = getParentFromRelative (userName, strCurrentServerPath);
            if (!sayNoToEmptyStrings (strParent))
                strParent = userName;

            NasMsg nm = netClient.goTo (strParent);
            if (nm == null || !workUpAListRequestResult (nm))
            {
                messageBox (ALERTHEADER_UNABLE_APPLY_AS_PATH, strParent, ERROR);
            }
        }
    }

//Если в списке/таблице выбрана папка, заходим в неё.
    @FXML public void onactionMenuClient_OpenFolder (ActionEvent actionEvent)
    {
        openFolderOnClientSide();
    }

//Если в списке/таблице выбрана папка, заходим в неё.
    @FXML public void onactionMenuServer_OpenFolder (ActionEvent actionEvent)
    {
        openFolderOnServerSide();
    }

//двойной щелчок ЛКМ по пункту-папке открывает соттв. ему папку
    @FXML public void tvOnMouseClickedClient (MouseEvent mouseEvent)
    {
        if (mouseEvent.getClickCount() == 2)
        {
           openFolderOnClientSide();
        }
    }

    @FXML public void tvOnMouseClickedServer (MouseEvent mouseEvent)
    {
        if (mouseEvent.getClickCount() == 2)
            openFolderOnServerSide();
    }

//-------------------------- гетеры и сетеры --------------------------------------------------------------------*/

    public TableView<TableFileInfo> getTvClient()  {   return tvClientSide;    }
    public TableView<TableFileInfo> getTvServer()  {   return tvServerSide;    }
    public NetClient getNetClient() {   return netClient;   }
    public String getStrCurrentLocalPath()  {   return strCurrentLocalPath;   }
    public TableColumn<TableFileInfo, String> getColumnClientFileName() {   return columnClientFileName;   }
    public TableColumn<TableFileInfo, String> getColumnServerFileName() {   return columnServerFileName;   }

//-------------------------- методы для работы с GUI ------------------------------------------------------------*/

//Заполняем таблицу элементами списка infolist.
    void populateTableView (@NotNull List<FileInfo> infolist, boolean local)
    {
        if (infolist != null)
        {
            long folders = 0L,  files = 0L;
            TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;
            String strPrefix = local ? STR_PREFIX_LOCAL : STR_PREFIX_REMOTE;

            String s = SBAR_TEXT_FOLDER_READING_IN_PROGRESS;
            if (local)  sbarSetDefaultText (s, null);
            else        sbarSetDefaultText (null, s);
            enableUsersInput (DISABLE);

            Point p = populateTv(tv, infolist);
            folders = p.x;
            files = p.y;

            enableUsersInput (ENABLE);
            s = String.format (SBAR_TEXTFORMAT_FOLDER_STATISTICS, strPrefix, folders, files);
            if (local)  sbarSetDefaultText (s, null);
            else        sbarSetDefaultText (null, s);
        }
    }

//Подсчитываем количества папок и файлов в указанной таблице и помещаем в строку состояния текст с полученной информацией.
    void collectStatistics (TableView<TableFileInfo> tv)
    {
        long folders = 0L,  files = 0L;
        boolean local = tv == tvClientSide;
        String strPrefix = local ? STR_PREFIX_LOCAL : STR_PREFIX_REMOTE;

        String s = CFactory.SBAR_TEXT_FOLDER_READING_IN_PROGRESS;
        if (local)  sbarSetDefaultText (s, null);
        else        sbarSetDefaultText (null, s);
        enableUsersInput (DISABLE);

        Point point = statisticsTv(tv);
        folders = point.x;
        files = point.y;

        enableUsersInput (ENABLE);
        s = String.format(CFactory.SBAR_TEXTFORMAT_FOLDER_STATISTICS, strPrefix, folders, files);
        if (local)  sbarSetDefaultText (s, null);
        else        sbarSetDefaultText (null, s);
    }

//Формируем текст, который будет по умолчанию отображаться в строке состояния. Левая часть отображает состояние клиента,
// правая — сервера. В качестве любого из параметров можно передать null, если соответствующую подстроку изменять не нужно.
    void sbarSetDefaultText (String local, String server)
    {
        if (local != null) sbarLocalStatistics = local;
        if (server != null) sbarServerStatistics = server;
        sbarTextDefault = String.format(CFactory.SBAR_TEXTFORMAT_STATISTICS, sbarLocalStatistics, sbarServerStatistics);
        textStatusBar.setText (sbarTextDefault);
    }

//Запрещаем или разрешаем использование элементов управления. (Используется во время выполнения длительных операций.)
    void enableUsersInput (boolean enable)
    {
        textfieldCurrentPath_Client.setDisable(!enable);
        textfieldCurrentPath_Server.setDisable(!enable);
        buttonDownload.setDisable (!enable && netClient != null);
        buttonUpload.setDisable (!enable && netClient != null);
        buttonLevelUp_Client.setDisable (!enable);
        buttonLevelUp_Server.setDisable (!enable);
        tvClientSide.setDisable (!enable);
        tvServerSide.setDisable (!enable);
    }

//Стандартное окно сообщения с кнопкой Ок.
    public static void messageBox (String header, String message, Alert.AlertType alerttype)
    {
        if (header == null) header = "";
        if (message == null) message = "";

        Alert a = new Alert (alerttype, message, ButtonType.CLOSE);
        a.setTitle(CFactory.ALERT_TITLE);
        a.setHeaderText (header);
        a.showAndWait();
    }

//Стандартное окно сообщения с кнопками Да и Нет.
    public static boolean messageBoxConfirmation (String header, String message, Alert.AlertType alerttype)
    {
        boolean boolOk = ANSWER_CANCEL;
        if (header == null) header = "";
        if (message == null) message = "";

        Alert a = new Alert (alerttype, message, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText (header);
        a.setTitle(CFactory.ALERT_TITLE);

        Optional<ButtonType> option = a.showAndWait();
        if (option.isPresent() && option.get() == ButtonType.OK)
        {
            boolOk = ANSWER_OK;
        }
        return boolOk;
    }

//------------------- Вспомогательные методы. Фактически, — поименованные куски кода. ---------------------------*/

//разбираем результат запроса LIST
//nm.msg    = относительный путь в папке юзера на сервере, или текст сообщения об ошибке.
//nm.data   = список содержимого этой папки (если нет ошибки).
//Возвращаем true, если nm.opCode() == OK. С остальным пусть разбирается вызывающая функция.
    @SuppressWarnings("unchecked")
    private boolean workUpAListRequestResult (NasMsg nm)
    {
        boolean ok = false;
        if (nm != null && nm.opCode() == OK)
        {
            ok = true;
            List<FileInfo> infolist = (List<FileInfo>) nm.data();
            if (infolist != null)
            {
                populateTableView (infolist, REMOTE);
                strCurrentServerPath = nm.msg();
                textfieldCurrentPath_Server.setText(nm.msg());
            }
            else lnprint("Не удалось вывести список содержимого папки.");
        }
        return ok;
    }

//считая указанную строку путём к каталогу, пытаемся отобразить содержимое этого каталога в «клиентской» панели.
    void applyStringAsNewLocalPath (String strPath)
    {
        if (!sayNoToEmptyStrings(strPath))
            strPath = System.getProperty (STR_DEF_FOLDER);

        if (isStringOfRealPath (strPath))
        {
            strCurrentLocalPath = strPath;
            List<FileInfo> infolist = listFolderContents (strCurrentLocalPath);
            populateTableView (infolist, LOCAL);
        }
        else messageBox(CFactory.PROMPT_DIR_ENTRY_DOESNOT_EXISTS, strPath, WARNING);
    }

//открывает локальную папку, которая соответствует выбранному пункту в таблице
    void openFolderOnClientSide()
    {
        TableFileInfo tfi = tvClientSide.getSelectionModel().getSelectedItem();
        if (tfi != null && tfi.getFolder())
        {
            lockOnWatching.lock();
            {
                String strPath = Paths.get (strCurrentLocalPath, tfi.getFileName()).toString();
                applyStringAsNewLocalPath (strPath);
                textfieldCurrentPath_Client.setText (strPath);
                clientWatcher.startWatchingOnFolder (strCurrentLocalPath);
            }
            lockOnWatching.unlock();
        }
    }

//открывает удалённую папку, которая соответствует выбранному пункту в таблице
    void openFolderOnServerSide()
    {
        TableFileInfo tfi = tvServerSide.getSelectionModel().getSelectedItem();
        if (tfi != null  &&  tfi.getFolder()  &&  netClient != null)
        {
            String strFolderName = tfi.getFileName();
            NasMsg nm = netClient.goTo (strCurrentServerPath, strFolderName);
            if (!workUpAListRequestResult (nm))
            {
                messageBox (ALERTHEADER_UNABLE_APPLY_AS_PATH, strFolderName, ERROR);
            }
        }
    }

    private void addTvItemAsFolder (@NotNull String name, boolean local)
    {
        if (tableViewManager != null)
        {
            TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;
            CFactory.addItemAsFolder(name, tv);
            collectStatistics (tv);
        }
    }

//генерируем имя для новой папки примерно так же, как это делаем Windows.
    private String generateDefaultFolderName (TableView<TableFileInfo> tv)
    {
        ObservableList<TableFileInfo> list = tv.getItems();

    //составляем список папок, чьи имена начинаются на NEW_FOLDER_NAME
        List<String> strsublist = list
            .stream()
            .filter(t->t.getFolder() && itemNameStartsWithString(t, CFactory.NEW_FOLDER_NAME))
            .map (TableFileInfo::getFileName)
            .collect (Collectors.toList());

    //добавляем к NEW_FOLDER_NAME номер, чтобы оно отличалось от имён отсальных папок
        String name = CFactory.NEW_FOLDER_NAME;
        if (!strsublist.isEmpty())
        {
            for (int i=1;   strsublist.contains(name);   i++)
            {
                name = String.format("%s (%d)", CFactory.NEW_FOLDER_NAME, i);
            }
        }
        return name;
    }

    public static boolean itemNameStartsWithString (TableFileInfo t, String prefix)
    {
        String name = t.getFileName().toLowerCase(),
               prfx = prefix.toLowerCase();
        return name.startsWith(prfx);
    }

    private boolean confirmEntryDeletion (String folderName, int entries)
    {
        boolean canDelete = false;
        if (entries > 0)
        {
            canDelete = messageBoxConfirmation (
                            ALERTHEADER_DELETION,
                            sformat(PROMPT_FORMAT_FOLDER_DELETION_CONFIRMATION, folderName),
                            CONFIRMATION);
        }
        else canDelete = entries == 0;
        return canDelete;
    }

//удаляем элемент текущего локального каталога, на который указывает елемент таблицы tfi.
    private boolean deleteLocalEntryByTfi (@NotNull TableFileInfo tfi)
    {
        FileInfo fi = tfi.toFileInfo();
        Path paim = Paths.get(strCurrentLocalPath, fi.getFileName());

        boolean ok = false;
        boolean error = false;
        boolean dir = fi.isDirectory();
        int entries = -1;

        if (dir)
        {
            entries = countDirectoryEntries(paim);
            error = entries < 0;
            ok = !error && (entries == 0 || confirmEntryDeletion (paim.toString(), entries));
        }
        else ok = confirmFileDeletion(paim.toString());

        if (ok)
        {
            ok = deleteFileOrDirectory (paim);
            if (tryLock(1000, MILLISECONDS))
                lockOnWatching.unlock();
            error = !ok;
        }
        if (error)  messageBox (ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR);
        return ok;
    }

//удаляем элемент текущего удалённого каталога, на который указывает елемент таблицы tfi.
    private boolean deleteRemoteEntryByIfi (@NotNull TableFileInfo tfi)
    {
        FileInfo fi = tfi.toFileInfo();
        String paim = Paths.get(strCurrentServerPath, fi.getFileName()).toString();

        boolean ok = false;
        boolean error = false;
        boolean dir = fi.isDirectory();
        int entries = -1;

        if (dir)
        {
            entries = netClient.countFolderEntries (strCurrentServerPath, fi);
            error = entries < 0;
            ok = !error && (entries == 0 || confirmEntryDeletion (paim, entries));
        }
        else ok = confirmFileDeletion(paim);

        if (ok)
        {
            OperationCodes opcode = netClient.delete (strCurrentServerPath, fi).opCode();
            ok = opcode == OK;
            error = opcode == OperationCodes.ERROR;
        }
        if (error)  messageBox(ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR);
        return ok;
    }

    private boolean confirmFileDeletion (String strFilePath)
    {
        return ANSWER_OK == messageBoxConfirmation (
                    ALERTHEADER_DELETION,
                    String.format(PROMPT_FORMAT_FILE_DELETION_CONFIRMATION, strFilePath),
                    CONFIRMATION);
    }

//загружаем файл с сервера в текущую локальную папку; если файл уже существует, запрашиваем подтверждение пользователя.
    private String downloadFileByTfi (TableFileInfo tfi)
    {
        String strErr = ERROR_UNABLE_TO_PERFORM;
        lockOnWatching.lock();  //< чтобы WatchService не реагировала на временную папку, которая создаётся при загрузке.
        {
            String strTarget = Paths.get(strCurrentLocalPath, tfi.getFileName()).toString();

            if (isItSafeToDownloadFile (strTarget))
            {
                NasMsg nm = netClient.download (strCurrentLocalPath, strCurrentServerPath, tfi.toFileInfo());
                if (nm != null)
                if (nm.opCode() == OperationCodes.ERROR)
                {
                    if (nm.msg() != null)
                        strErr = nm.msg();
                }
                else if (nm.opCode() == OK)
                {
                    // Из-за ошибки с передачей больших файлов мы не можем просто добавить пункт
                    // в таблицу. Поэтому пойдём длинным путём — обновим список файлов.
                    applyStringAsNewLocalPath (strCurrentLocalPath);
                    strErr = null;
                    //Листенер, как оказалось, тут тоже использовать сложно, т.к. он реагирует на
                    //  временную папку, которая создаётся при загрузке.
                }
            }
        }
        lockOnWatching.unlock();
        return strErr;
    }

//проверяем, существует ли файл и, если существует, запрашиваем у юзера подтверждение на перезапись
    private boolean isItSafeToDownloadFile (String strTarget)
    {
        return !isStringOfRealPath (strTarget)
                || ANSWER_OK == messageBoxConfirmation (CFactory.ALERTHEADER_DOWNLOADING,
                                                        String.format(CFactory.PROMPT_FORMAT_REPLACE_CONFIRMATION, strTarget),
                                                        Alert.AlertType.CONFIRMATION);
    }

//выгружаем файл на сервера в текущую удалённую папку; если файл уже существует, запрашиваем подтверждение пользователя.
    private String uploadFileByTfi (TableFileInfo tfi)
    {
        String strTargetName = tfi.getFileName();
        String strErr = ERROR_UNABLE_TO_PERFORM + " %";
        FileInfo fi = netClient.fileInfo (strCurrentServerPath, strTargetName);

        if (fi != null)
        if (fi.isDirectory())
        {
            messageBox (ALERTHEADER_LOCAL2SERVER, PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED, WARNING);
        }
        else if (isItSafeToUploadFile (strTargetName, fi.isExists()))
        {
            NasMsg nm = netClient.upload (strCurrentLocalPath, strCurrentServerPath, tfi.toFileInfo());
            if (nm != null)
            if (workUpAListRequestResult (netClient.list (strCurrentServerPath)))
            {   // Из-за ошибки с передачей больших файлов мы не можем просто добавить пункт в таблицу.
                // Поэтому пойдём длинным путём — обновим список файлов.
                //TODO : правильнее было бы сделать листенер на текущую папку.
                strErr = null;
            }
            else if (nm.opCode() == OperationCodes.ERROR)
            {
                if (nm.msg() != null)
                    strErr = nm.msg();
            }
        }
        return strErr;
    }

//проверяем, существует ли файл и, если существует, запрашиваем у юзера подтверждение на перезапись
    private boolean isItSafeToUploadFile (String strTargetName, boolean exists)
    {
        String strPath = Paths.get (strCurrentServerPath, strTargetName).toString();

        return !exists || ANSWER_OK == messageBoxConfirmation (CFactory.ALERTHEADER_UPLOADING,
                                                               String.format(CFactory.PROMPT_FORMAT_REPLACE_CONFIRMATION, strPath),
                                                               Alert.AlertType.CONFIRMATION);
    }

    private boolean tryLock (long time, TimeUnit timeUnits)
    {
        boolean ok = false;
        try {   ok = lockOnWatching.tryLock (time, timeUnits);
            }
        catch (InterruptedException e){e.printStackTrace();}
        return ok;
    }
}
//---------------------------------------------------------------------------------------------------------------*/
