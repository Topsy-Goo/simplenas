package ru.gb.simplenas.client;

import com.sun.istack.internal.NotNull;
import javafx.application.Platform;
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
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.client.services.impl.TableViewManager;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;
import ru.gb.simplenas.common.structs.TableFileInfo;

import java.awt.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static javafx.scene.control.Alert.AlertType.*;
import static javafx.scene.control.Alert.AlertType.ERROR;
import static ru.gb.simplenas.client.CFactory.*;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
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
    private boolean donotShowDisconnectionMessage = false;
    private boolean extraInitialisationIsDone = false;
    private TableViewManager tableViewManager;
    private static final Logger LOGGER = LogManager.getLogger(Controller.class.getName());


//TODO : Тесты
//TODO : Проперти
//TODO : Грэдл
//TODO : авторизация + БД + флайвэй
//TODO : наблюдение за изменениями в локальной папке.
//TODO : DnD
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

    @Override public void initialize (URL location, ResourceBundle resources)       //+l
    {
        LOGGER.trace("initialize() start");
        javafx = Thread.currentThread();

        strCurrentLocalPath = System.getProperty(CFactory.STR_DEF_FOLDER);
        sbarSetDefaultText(STR_EMPTY, CFactory.SBAR_TEXT_SERVER_NOCONNECTION);

        tableViewManager = new TableViewManager (this);
        populateTableView (listFolderContents (strCurrentLocalPath), LOCAL);
        textfieldCurrentPath_Client.setText (strCurrentLocalPath);
        textfieldCurrentPath_Server.setPromptText(CFactory.TEXTFIELD_SERVER_PROMPTTEXT_DOCONNECT);

        CFactory.setContextMenuEventHandler_OnShoing(menuClientTableActions, tvClientSide);
        CFactory.setContextMenuEventHandler_OnShoing(menuServerTableActions, tvServerSide);

        extraInitialisationIsDone = false;
        enableUsersInput (ENABLE);
        LOGGER.trace("initialize() end");
    }

    void onCmdConnectAndLogin (String name)     //+l
    {
        LOGGER.trace("onCmdConnectAndLogin("+name+") start");
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
            else messageBox(CFactory.ALERTHEADER_CONNECTION, CFactory.ERROR_UNABLE_CONNECT_TO_SERVER, WARNING);
        }
        LOGGER.trace("onCmdConnectAndLogin() end");
    }

    private boolean connect()       //+
    {
        netClient = CFactory.newNetClient((ooo)->callbackOnNetClientDisconnection());
        donotShowDisconnectionMessage = !netClient.connect();
        return !donotShowDisconnectionMessage;
    }

    private void login (String name)        //+l
    {
        NasMsg nm = null;
        if (netClient == null || (null == (nm = netClient.login (name))))
        {
            messageBox(CFactory.ALERTHEADER_AUTHENTIFICATION, ERROR_UNABLE_TO_PERFORM, ERROR);
        }
        else if (nm.opCode() == OK)
        {
            userName = name;
            updateControlsOnSuccessfulLogin();
            LOGGER.info("авторизован пользователь "+ userName);
        }
        else if (nm.opCode() == OperationCodes.ERROR)
        {
            String strErr = nm.msg();
            if (!sayNoToEmptyStrings (strErr))
            {
                strErr = ERROR_UNABLE_TO_PERFORM;
            }
            messageBox(CFactory.ALERTHEADER_AUTHENTIFICATION, strErr, WARNING);
        }
    }

    private void updateControlsOnSuccessfulLogin()      //+
    {
        textfieldCurrentPath_Server.clear();
        textfieldCurrentPath_Server.setText(STR_EMPTY);
        textfieldCurrentPath_Server.setPromptText(CFactory.TEXTFIELD_SERVER_PROMPTTEXT_LOGGEDIN);
        sbarSetDefaultText(null, CFactory.SBAR_TEXT_SERVER_ONAIR);

        messageBox (CFactory.ALERTHEADER_AUTHENTIFICATION,
                    String.format(CFactory.STRFORMAT_YOUARE_LOGGEDIN, userName),
                    INFORMATION);
        workUpAListRequestResult (netClient.list (userName));
        updateMainWndTitleWithUserName();
    }

// Наш обработчик события primaryStage.setOnCloseRequest.
    void closeSession()         //+l
    {
        LOGGER.trace("closeSession() start");
        donotShowDisconnectionMessage = true;
        if (netClient != null)
        {
            netClient.disconnect();  //< это приведёт к разрыву соединения с сервером (к закрытию канала) и к вызову onNetClientDisconnection()
        }
        LOGGER.trace("closeSession() end");
    }

    void onMainWndShowing (Stage primaryStage)         //+
    {
        this.primaryStage = primaryStage;
    }

//callback. Вызывается из netClient при закрытии соединения с сервером. Может вызываться дважды для закрытия одной и той же сессии.
    void callbackOnNetClientDisconnection()     //+l
    {
        LOGGER.trace("callbackOnNetClientDisconnection() start");
        netClient = null;
        userName = null;
        if (!donotShowDisconnectionMessage)
        {
            Platform.runLater(()->{
                messageBox(CFactory.ALERTHEADER_REMOUTE_STORAGE, PROMPT_CONNECTION_GETTING_CLOSED, WARNING);  //< вызов не из потока javafx вызывает исключение
                                  });
        }
        sbarSetDefaultText(null, CFactory.SBAR_TEXT_SERVER_NOCONNECTION);
        LOGGER.trace("callbackOnNetClientDisconnection() end");
    }

    private void updateMainWndTitleWithUserName()         //+
    {
        if (primaryStage != null)
        {
            String newtitle = String.format("%s - вы вошли как : %s", MAINWND_TITLE, sayNoToEmptyStrings(userName) ? userName : CFactory.NO_USER_TITLE);
            primaryStage.setTitle (newtitle);
        }
    }

//------------------------------------------ обработчики команд GUI ---------------------------------------------*/

//Создание папки на сервере в текущей папке.
    @FXML public void onactionMenuServer_CreateFolder (ActionEvent actionEvent)     //+l
    {
        LOGGER.trace("!!!!!!! onactionMenuServer_CreateFolder");
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
        if (errMsg != null)   messageBox(CFactory.ALERTHEADER_REMOUTE_STORAGE, errMsg, ERROR);
    }

//Создание локальной папки в текущей папке.
    @FXML public void onactionMenuClient_CreateFolder (ActionEvent actionEvent)      //+l
    {
        LOGGER.trace("!!!!!!! onactionMenuClient_CreateFolder");
        String name = generateDefaultFolderName (tvClientSide);

        if (null != createSubfolder (Paths.get(strCurrentLocalPath), name))
        {
            addTvItemAsFolder(name, LOCAL);
        }
        else messageBox(CFactory.ALERTHEADER_REMOUTE_STORAGE, ERROR_UNABLE_TO_PERFORM, ERROR);
    }

//в текущей клиентской папке удаляем выбранный элемент (файл или папку).
    @FXML public void onactionClientDelete (ActionEvent actionEvent)       //+l
    {
        LOGGER.trace("!!!!!!! onactionClientDelete");
        TableFileInfo tfi = tvClientSide.getSelectionModel().getSelectedItem();
        String errMsg = null;

        if (tfi == null)
        {
            errMsg = ERROR_UNABLE_TO_PERFORM;
        }
        else if (deleteLocalEntryByTfi (tfi))
        {
            deleteTvItem (tvClientSide, tfi);
            collectStatistics (tvClientSide);
        }
        if (errMsg != null) messageBox(ALERTHEADER_DELETION, errMsg, ERROR);
    }

//в текущей серверной папке удаляем выбранный элемент (файл или папку).
    @FXML public void onactionServerDelete (ActionEvent actionEvent)      //+l
    {
        LOGGER.trace("!!!!!!! onactionServerDelete");
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
        if (errMsg != null)  messageBox(CFactory.ALERTHEADER_REMOUTE_STORAGE, errMsg, WARNING);

    }

//Загрузка одного выбранного элемента-файла с сервера в текущую локальную папку
    @FXML public void onactionDownload (ActionEvent actionEvent)        //+l
    {
        LOGGER.trace("!!!!!!! onactionDownload");
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
                errMsg = CFactory.PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED;
                alertType = INFORMATION;
            }
            else errMsg = downloadFileByTfi (tfi);
        }
        if (errMsg != null)  messageBox(CFactory.ALERTHEADER_REMOUTE_STORAGE, errMsg, alertType);
    }

//Выгрузка одного выбранного элемента-файла из локальной папки на сервер в текущую папку
    @FXML public void onactionUpload (ActionEvent actionEvent)      //+l
    {
        LOGGER.trace("!!!!!!! onactionUpload");
        TableFileInfo tfi;
        String errMsg = null;
        Alert.AlertType alertType = ERROR;

        if (netClient == null)
        {
            errMsg = CFactory.ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
            alertType = WARNING;
        }
        else if (null != (tfi = tvClientSide.getSelectionModel().getSelectedItem()))
        {
            //загрузка папок (даже пустых) не реализована
            if (tfi.getFolder())
            {
                errMsg = CFactory.PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED;
                alertType = INFORMATION;
            }
            else errMsg = uploadFileByTfi (tfi);
        }
        if (errMsg != null)   messageBox(CFactory.ALERTHEADER_LOCAL_STORAGE, errMsg, alertType);
    }

//В поле ввода textfieldCurrentPath_Client нажат ENTER.
    @FXML public void onactionTextField_Client_ApplyAsPath (ActionEvent actionEvent) //+l
    {
        LOGGER.trace("!!!!!!! onactionTextField_Client_SelectFolder");
        applyStringAsNewLocalPath (textfieldCurrentPath_Client.getText());
    }

//В поле ввода textfieldCurrentPath_Server нажат ENTER.
    @FXML public void onactionTextField_Server_ApplyAsPath (ActionEvent actionEvent) //+l
    {
        LOGGER.trace("!!!!!!! onactionTextField_Server_SelectFolder");
        String text = textfieldCurrentPath_Server.getText().trim();

        if (netClient == null)
        {   //перед подключением поле пути для сервера используется как поле ввода для имени пользователя
            onCmdConnectAndLogin(text);
        }
        else
        {   //если подключение уже установлено, то текст явялется папкой на сервере, которую нужно выбрать
            workUpAListRequestResult (netClient.goTo (text));
        }
    }

//Переходим в родительскую папку, если таковая существует.
    @FXML public void onactionButton_Client_LevelUp (ActionEvent actionEvent)    //+l
    {
        LOGGER.trace("!!!!!!! onactionButton_Client_LevelUp");
        // !!! Игнорируем содержимое поля ввода textfieldCurrentPath_Client)
        String strParent = stringPath2StringAbsoluteParentPath (strCurrentLocalPath);

        if (strParent != null)
        if (strParent.isEmpty())
        {
            messageBox (CFactory.ALERTHEADER_LOCAL_STORAGE,
                        String.format(CFactory.PROMPT_FORMAT_ROOT_FOLDER, strCurrentLocalPath),
                        INFORMATION);
        }
        else
        {   populateTableView (listFolderContents(strParent), LOCAL);
            strCurrentLocalPath = strParent;
            textfieldCurrentPath_Client.setText(strCurrentLocalPath);
        }
    }

//Переходим в родительскую папку, если таковая существует.
    @FXML public void onactionButton_Server_LevelUp (ActionEvent actionEvent)    //+l
    {
        LOGGER.trace("!!!!!!! onactionButton_Server_LevelUp");
        if (netClient != null)
        {
            // !!! Игнорируем содержимое поля ввода textfieldCurrentPath_Server)
            NasMsg nm = netClient.levelUp (strCurrentServerPath);
            if (nm != null)
            {
                workUpAListRequestResult (nm);
            }
            else
            {   messageBox (CFactory.ALERTHEADER_REMOUTE_STORAGE,
                            String.format(CFactory.PROMPT_FORMAT_ROOT_FOLDER, strCurrentServerPath),
                            INFORMATION);
            }
        }
    }

//Если в списке/таблице выбрана папка, заходим в неё.
    @FXML public void onactionMenuClient_OpenFolder (ActionEvent actionEvent)   //+l
    {
        LOGGER.trace("!!!!!!! onactionMenuClient_Open");
        openFolderOnClientSide();
    }

//Если в списке/таблице выбрана папка, заходим в неё.
    @FXML public void onactionMenuServer_OpenFolder (ActionEvent actionEvent)   //+l
    {
        LOGGER.trace("!!!!!!! onactionMenuServer_Open.");
        openFolderOnServerSide();
    }

    @FXML public void tvClientOnMouseClicked (MouseEvent mouseEvent)    //+l
    {
        LOGGER.trace("!!!!!!! tvClientOnMouseClicked()");
        if (mouseEvent.getClickCount() == 2)
           openFolderOnClientSide();
    }

    @FXML public void tvServerOnMouseClicked (MouseEvent mouseEvent)    //+l
    {
        LOGGER.trace("!!!!!!! tvServerOnMouseClicked()");
        if (mouseEvent.getClickCount() == 2)
            openFolderOnServerSide();
    }

    @FXML public void onactionMenuServer_CutFolder (ActionEvent actionEvent)    {   if (DEBUG) lnprint ("!!!!!!! onactionMenuServer_CutFolder");    }
    @FXML public void onactionMenuServer_PasteFolder (ActionEvent actionEvent)  {   if (DEBUG) lnprint ("!!!!!!! onactionMenuServer_PasteFolder");    }


//-------------------------- гетеры и сетеры --------------------------------------------------------------------*/

    public TableView<TableFileInfo> getTvClient()  {   return tvClientSide;    }
    public TableView<TableFileInfo> getTvServer()  {   return tvServerSide;    }
    public NetClient getNetClient() {   return netClient;   }
    public String getStrCurrentLocalPath()  {   return strCurrentLocalPath;   }
    public TableColumn<TableFileInfo, String> getColumnClientFileName() {   return columnClientFileName;   }
    public TableColumn<TableFileInfo, String> getColumnServerFileName() {   return columnServerFileName;   }

//-------------------------- методы для работы с GUI ------------------------------------------------------------*/

//Заполняем таблицу элементами списка infolist.
    void populateTableView (@NotNull List<FileInfo> infolist, boolean local)    //+
    {
        if (infolist != null)
        {
            long folders = 0L,  files = 0L;
            TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;

            String s = CFactory.SBAR_TEXT_FOLDER_READING_IN_PROGRESS;
            if (local)  sbarSetDefaultText (s, null);
            else        sbarSetDefaultText (null, s);
            enableUsersInput (DISABLE);

            Point p = CFactory.populateTv(tv, infolist);
            folders = p.x;
            files = p.y;

            enableUsersInput (ENABLE);
            s = String.format(CFactory.SBAR_TEXTFORMAT_FOLDER_STATISTICS, folders, files);
            if (local)  sbarSetDefaultText (s, null);
            else        sbarSetDefaultText (null, s);
        }
    }

//Подсчитываем количества папок и файлов в указанной таблице и помещаем в строку состояния текст с полученной информацией.
    void collectStatistics (TableView<TableFileInfo> tv)        //+
    {
        long folders = 0L,  files = 0L;
        boolean local = tv == tvClientSide;

        String s = CFactory.SBAR_TEXT_FOLDER_READING_IN_PROGRESS;
        if (local)  sbarSetDefaultText (s, null);
        else        sbarSetDefaultText (null, s);
        enableUsersInput (DISABLE);

        Point point = CFactory.statisticsTv(tv);
        folders = point.x;
        files = point.y;

        enableUsersInput (ENABLE);
        s = String.format(CFactory.SBAR_TEXTFORMAT_FOLDER_STATISTICS, folders, files);
        if (local)  sbarSetDefaultText (s, null);
        else        sbarSetDefaultText (null, s);
    }

//Формируем текст, который будет по умолчанию отображаться в строке состояния. Левая часть отображает состояние клиента,
// правая — сервера. В качестве любого из параметров можно передать null, если соответствующую подстроку изменять не нужно.
    void sbarSetDefaultText (String local, String server)       //+
    {
        if (local != null) sbarLocalStatistics = local;
        if (server != null) sbarServerStatistics = server;
        sbarTextDefault = String.format(CFactory.SBAR_TEXTFORMAT_STATISTICS, sbarLocalStatistics, sbarServerStatistics);
        textStatusBar.setText (sbarTextDefault);
    }

//Запрещаем или разрешаем использование элементов управления. (Используется во время выполнения длительных операций.)
    void enableUsersInput (boolean mode)        //+
    {
        textfieldCurrentPath_Client.setDisable(mode);
        textfieldCurrentPath_Server.setDisable(mode);
        buttonDownload.setDisable (mode && netClient != null);
        buttonUpload.setDisable (mode && netClient != null);
        buttonLevelUp_Client.setDisable(mode);
        buttonLevelUp_Server.setDisable(mode);
        tvClientSide.setDisable(mode);
        tvServerSide.setDisable(mode);
    }

//Стандартное окно сообщения с кнопкой Ок.
    public static void messageBox (String header, String message, Alert.AlertType alerttype)    //+
    {
        if (header == null) header = "";
        if (message == null) message = "";

        Alert a = new Alert (alerttype, message, ButtonType.CLOSE);
        a.setTitle(CFactory.ALERT_TITLE);
        a.setHeaderText (header);
        a.showAndWait();
    }

//Стандартное окно сообщения с кнопками Да и Нет.
    public static boolean messageBoxConfirmation (String header, String message, Alert.AlertType alerttype)     //+
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
    @SuppressWarnings("unchecked")
    private void workUpAListRequestResult (NasMsg nm)   //+
    {
        boolean ok = false;
        String errMsg = CFactory.ERROR_UNABLE_GET_LIST;

        if (nm != null)
        {
            if (nm.opCode() == OK)
            {
                List<FileInfo> infolist = (List<FileInfo>) nm.data();
                populateTableView (infolist, REMOTE);
                ok = infolist != null;
                strCurrentServerPath = nm.msg();
                textfieldCurrentPath_Server.setText(nm.msg());
            }
            else if (nm.opCode() == OperationCodes.ERROR)
            {
                errMsg = nm.msg();
            }
        }
        if (!ok) messageBox(CFactory.ALERTHEADER_REMOUTE_STORAGE, errMsg, ERROR);
    }

//считая указанную строку путём к каталогу, пытаемся отобразить содержимое этого каталога в «клиентской» панели.
    private void applyStringAsNewLocalPath (String strPath)     //+
    {
        if (strPath == null || strPath.isEmpty())
            strPath = System.getProperty(CFactory.STR_DEF_FOLDER);

        if (isStringOfRealPath (strPath))
        {
            strCurrentLocalPath = strPath;
            List<FileInfo> infolist = listFolderContents (strCurrentLocalPath);
            populateTableView (infolist, LOCAL);
        }
        else messageBox(CFactory.PROMPT_DIR_ENTRY_DOESNOT_EXISTS, strPath, WARNING);
    }

//открывает локальную папку, которая соответствует выбранному пункту в таблице
    void openFolderOnClientSide()   //+
    {
        TableFileInfo tfi = tvClientSide.getSelectionModel().getSelectedItem();
        if (tfi != null && tfi.getFolder())
        {
            String strPath = Paths.get (strCurrentLocalPath, tfi.getFileName()).toString();
            applyStringAsNewLocalPath (strPath);
            textfieldCurrentPath_Client.setText (strPath);
        }
    }

//открывает удалённую папку, которая соответствует выбранному пункту в таблице
    void openFolderOnServerSide()   //+
    {
        TableFileInfo tfi = tvServerSide.getSelectionModel().getSelectedItem();
        if (tfi != null  &&  tfi.getFolder()  &&  netClient != null)
        {
            NasMsg nm = netClient.goTo (strCurrentServerPath, tfi.getFileName());
            workUpAListRequestResult (nm);
        }
    }

    private void addTvItemAsFolder (@NotNull String name, boolean local)     //+
    {
        if (tableViewManager != null)
        {
            TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;
            CFactory.addItemAsFolder(name, tv);
            collectStatistics (tv);
        }
    }

//генерируем имя для новой папки примерно так же, как это делаем Windows.
    private String generateDefaultFolderName (TableView<TableFileInfo> tv)  //+
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

    public static boolean itemNameStartsWithString (TableFileInfo t, String prefix)     //+
    {
        String name = t.getFileName().toLowerCase(),
               prfx = prefix.toLowerCase();
        return name.startsWith(prfx);
    }

    private boolean confirmEntryDeletion (String folderName, int entries)   //+
    {
        boolean canDelete = false;
        if (entries > 0)
        {
            canDelete = messageBoxConfirmation (ALERTHEADER_DELETION,
                                                String.format(CFactory.PROMPT_FORMAT_FOLDER_DELETION_CONFIRMATION, folderName),
                                                Alert.AlertType.CONFIRMATION);
        }
        else canDelete = entries == 0;
        return canDelete;
    }

//удаляем элемент текущего локального каталога, на который указывает елемент таблицы tfi.
    private boolean deleteLocalEntryByTfi (@NotNull TableFileInfo tfi)        //+
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
            error = !ok;
        }
        if (error)  messageBox(ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR);
        return ok;
    }

//удаляем элемент текущего удалённого каталога, на который указывает елемент таблицы tfi.
    private boolean deleteRemoteEntryByIfi (@NotNull TableFileInfo tfi)      //+
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

    private boolean confirmFileDeletion (String strFilePath)        //+
    {
        return ANSWER_OK == messageBoxConfirmation (
                    ALERTHEADER_DELETION,
                    String.format(PROMPT_FORMAT_FILE_DELETION_CONFIRMATION, strFilePath),
                    CONFIRMATION);
    }

//загружаем файл с сервера в текущую локальную папку; если файл уже существует, запрашиваем подтверждение пользователя.
    private String downloadFileByTfi (TableFileInfo tfi)     //+
    {
        String strTarget = Paths.get(strCurrentLocalPath, tfi.getFileName()).toString();
        String strErr = ERROR_UNABLE_TO_PERFORM;

        if (isItSafeToDownloadFile (strTarget))
        {
            NasMsg nm = netClient.transferFile (strCurrentLocalPath, strCurrentServerPath,
                                                tfi.toFileInfo(), LOAD2LOCAL);
            if (nm != null)
            if (nm.opCode() == OperationCodes.ERROR)
            {
                strErr = nm.msg();
            }
            else if (nm.opCode() == OK)
            {
                applyStringAsNewLocalPath (strCurrentLocalPath);
                strErr = null;
                // Из-за ошибки с передачей больших файлов мы не можем просто добавить пункт
                // в таблицу. Поэтому пойдём длинным путём — обновим список файлов.
                //TODO : правильнее было бы сделать листенер на текущую папку.
            }
        }
        return strErr;
    }

//проверяем, существует ли файл и, если существует, запрашиваем у юзера подтверждение на перезапись
    private boolean isItSafeToDownloadFile (String strTarget)   //+
    {
        return !isStringOfRealPath (strTarget)
                || ANSWER_OK == messageBoxConfirmation (CFactory.ALERTHEADER_DOWNLOADING,
                                                        String.format(CFactory.PROMPT_FORMAT_REPLACE_CONFIRMATION, strTarget),
                                                        Alert.AlertType.CONFIRMATION);
    }

//выгружаем файл на сервера в текущую удалённую папку; если файл уже существует, запрашиваем подтверждение пользователя.
    private String uploadFileByTfi (TableFileInfo tfi)      //+
    {
        String strTargetName = tfi.getFileName();
        String strErr = ERROR_UNABLE_TO_PERFORM;
        FileInfo fi = netClient.fileInfo (strCurrentServerPath, strTargetName);

        if (fi != null)
        if (fi.isDirectory())
        {
            messageBox(CFactory.ALERTHEADER_REMOUTE_STORAGE, CFactory.PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED, WARNING);
        }
        else if (isItSafeToUploadFile (strTargetName, fi.isExists()))
        {
            NasMsg nm = netClient.transferFile (strCurrentLocalPath, strCurrentServerPath,
                                                tfi.toFileInfo(), LOAD2SERVER);
            if (nm != null)
            if (nm.opCode() == OperationCodes.ERROR)
            {
                strErr = nm.msg();
            }
            else if (nm.opCode() == OK)
            {
                workUpAListRequestResult (netClient.list (strCurrentServerPath));
                strErr = null;
                // Из-за ошибки с передачей больших файлов мы не можем просто добавить пункт в таблицу.
                // Поэтому пойдём длинным путём — обновим список файлов.
                //TODO : правильнее было бы сделать листенер на текущую папку.
            }
        }
        return strErr;
    }

//проверяем, существует ли файл и, если существует, запрашиваем у юзера подтверждение на перезапись
    private boolean isItSafeToUploadFile (String strTargetName, boolean exists)     //+
    {
        String strPath = Paths.get (strCurrentServerPath, strTargetName).toString();

        return !exists || ANSWER_OK == messageBoxConfirmation (CFactory.ALERTHEADER_UPLOADING,
                                                               String.format(CFactory.PROMPT_FORMAT_REPLACE_CONFIRMATION, strPath),
                                                               Alert.AlertType.CONFIRMATION);
    }

}// class Controller
//---------------------------------------------------------------------------------------------------------------*/
