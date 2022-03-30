package ru.gb.simplenas.client;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import ru.gb.simplenas.client.services.ClientPropertyManager;
import ru.gb.simplenas.client.services.ClientWatchService;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.client.services.impl.ContextMenuManager;
import ru.gb.simplenas.client.services.impl.LocalWatchService;
import ru.gb.simplenas.client.services.impl.TableViewManager;
import ru.gb.simplenas.client.structs.TableFileInfo;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static javafx.scene.control.Alert.AlertType.*;
import static ru.gb.simplenas.client.CFactory.*;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.services.impl.NasFileManager.*;
import static ru.gb.simplenas.common.structs.OperationCodes.NM_OPCODE_ERROR;
import static ru.gb.simplenas.common.structs.OperationCodes.NM_OPCODE_OK;

public class Controller implements Initializable {

    private final static Logger LOGGER   = LogManager.getLogger(Controller.class.getName());
    private       static int    fontSize = DEFAULT_FONT_SIZE;
    private final        Object syncObj  = new Object();
    //private Thread javafx;

    @FXML public VBox      rootbox;
    @FXML public Text      textStatusBar;
    @FXML public TextField textfieldCurrentPath_Client;
    @FXML public TextField textfieldCurrentPath_Server;
    @FXML public Button    buttonDownload;
    @FXML public Button    buttonUpload;
    @FXML public Button    buttonLevelUp_Client;
    @FXML public Button    buttonLevelUp_Server;
    @FXML public ContextMenu              menuClientTableActions;
    @FXML public ContextMenu              menuServerTableActions;
    @FXML public TableView<TableFileInfo> tvClientSide;
    @FXML public TableView<TableFileInfo> tvServerSide;
    @FXML public TableColumn<TableFileInfo, String> columnClientFileName;
    @FXML public TableColumn<TableFileInfo, String> columnServerFileName;
    @FXML public Button buttonConnect;

    private String userName;
    private String strCurrentLocalPath  = STR_EMPTY; // не имеет отношения к textfieldCurrentPath_Client
    private String strCurrentServerPath = STR_EMPTY;// не имеет отношения к textfieldCurrentPath_Server
    private String sbarTextDefault      = STR_EMPTY;
    private String sbarLocalStatistics  = STR_EMPTY;
    private String sbarServerStatistics = STR_EMPTY;

    private Stage     primaryStage;
    private Stage     dlgLoginStage;
    private NetClient netClient;
    private DlgLoginController    dlgLoginController;
    private TableViewManager      tableViewManager;
    private ClientPropertyManager propMan;
    private ClientWatchService    clientWatcher;
    //private Lock                  lockSuspendWatching;
    private ContextMenuManager    contextMenuManager;

//------------------------------- инициализация и завершение ----------------------------------------------------*/

    @Override public void initialize (URL location, ResourceBundle resources)
    {
        LOGGER.debug ("initialize() starts");
    //считывание настроек:
        propMan = getProperyManager();
        netClient = newNetClient (ooo->callbackOnNetClientDisconnection(),
                                  propMan.getRemotePort(),
                                  propMan.getHostString());
        strCurrentLocalPath = propMan.getDefaultLastLocalPathString();
        setSceneFontSize (rootbox, propMan.getDefaultFontSize());

    //TableView specific:
        tableViewManager = new TableViewManager (this, this::callbackUpdateView);
        contextMenuManager = new ContextMenuManager (this);
        contextMenuManager.setEventHandler_OnShoing (menuClientTableActions, tvClientSide);
        contextMenuManager.setEventHandler_OnShoing (menuServerTableActions, tvServerSide);

    //то, что можно сделать в последнюю очередь:
        textfieldCurrentPath_Client.setText (strCurrentLocalPath);
        applyStringAsNewLocalPath (strCurrentLocalPath);
        sbarSetText (null, SBAR_TEXT_SERVER_NOCONNECTION);
        LOGGER.debug ("C.initialize() finished");
    }

/** Обработчик события «Явление окна приложения очам юзера». Вызывается единажды при старте приложения. */
    void onMainWndShowing (Stage primary) //< не надо делать этот метод private!
    {
        primaryStage = primary;
        enableUsersInput (ENABLE);
        clientWatcher = getWatchServiceInstance();
        clientWatcher.setCallBack (this::callbackOnCurrentFolderEvents);
        clientWatcher.startWatchingOnFolder (strCurrentLocalPath);
    }

/** Только подключения и авторизация. */
    private void connectAndLogin ()
    {
        if (netClient == null || !netClient.connect())
            messageBox (ALERTHEADER_CONNECTION, ERROR_UNABLE_CONNECT_TO_SERVER, WARNING);
        else {
            String login = dlgLoginController.getLogin();
            String password = dlgLoginController.getPassword();
            dlgLoginController.txtfldPassword.clear();
            dlgLoginController.txtfldLogin.clear();

            NasMsg nm = netClient.login (login, password);
            if (nm == null)
                messageBox (ALERTHEADER_AUTHENTIFICATION, ERROR_UNABLE_TO_PERFORM, ERROR);
            else
            if (nm.opCode() == NM_OPCODE_OK) {
                userName = login;
                updateControlsOnConnectionStatusChanged(CONNECTED);
            }
            else if (nm.opCode() == NM_OPCODE_ERROR) {
                String strErr = nm.msg();
                if (!sayNoToEmptyStrings (strErr))
                    strErr = ERROR_UNABLE_TO_PERFORM;
                messageBox (ALERTHEADER_AUTHENTIFICATION, strErr, WARNING);
            }
        }
    }

/** Обновляем GUI приложения в соответствии со статусом сетевого подключения.<p>
    При подключении изменяются настройки приложения в соотствии с сохранёнными ранее предпочтениями
    юзера, и считывается содержимое удалённой папки юзера.<p>
    При отключении сетевого соединения настройки-предпочтения НЕ сбрасываются на дефолтные. */
    private void updateControlsOnConnectionStatusChanged (boolean connected)
    {
        updateMainWndTitleWithUserName (userName);
        swapControlsOnConnect (connected);
        sbarSetText (null, connected ? SBAR_TEXT_SERVER_ONAIR : SBAR_TEXT_SERVER_NOCONNECTION);

        if (connected) {
            readUserSpecificProperties (userName);
            messageBox (ALERTHEADER_AUTHENTIFICATION,
                        sformat (STRFORMAT_YOUARE_LOGGEDIN, userName),
                        INFORMATION);
            if (!workUpAListRequestResult (netClient.list (strCurrentServerPath)))
                messageBox (ALERTHEADER_REMOUTE_STORAGE,
                            sformat (PROMPT_FORMAT_UNABLE_LIST, userName),
                            ERROR);
        }
        else clearTv (tvServerSide);
    }

/** Наш обработчик события primaryStage.setOnCloseRequest. Вызывается, когда юзер закрывает окно прилодения. */
    void closeSession ()
    {
        storeProperties();  //< это нужно сделать до того, как userName станет == null
        disconnect();

        if (clientWatcher != null)
            clientWatcher.close();
    }

/** Сохраняем предпочтения пользователя. Предпочтения делятся на общие и личные. */
    private void storeProperties ()
    {
        if (propMan != null) {
            if (!sayNoToEmptyStrings (userName))
                propMan.setLastLocalPath (strCurrentLocalPath);
            else {
                propMan.setUserLastLocalPath (userName, strCurrentLocalPath);
                propMan.setUserLastRemotePath (userName, strCurrentServerPath);
            }
            propMan.close();
        }
    }

/**  */
    private void disconnect () {
        if (netClient != null)  //это приведёт к разрыву соединения с сервером (к закрытию канала) и к вызову onNetClientDisconnection()
            netClient.disconnect();
    }
//------------------------------------------ колбэки --------------------------------------

/** callback. Вызывается из netClient при закрытии соединения с сервером. Может вызываться дважды для закрытия одной и той же сессии. */
    private void callbackOnNetClientDisconnection ()
    {
        storeProperties();
        userName = null;
        Platform.runLater(()->{
        //    messageBox(ALERTHEADER_REMOUTE_STORAGE,
        //               PROMPT_CONNECTION_GETTING_CLOSED,
        //               WARNING);  //< вызов не из потока javafx вызывает исключение
            updateControlsOnConnectionStatusChanged (NOT_CONNECTED);
        });
    }

/** Колбэк для службы наблюдения за текущей папкой. Метод вызывается из ClientWatchService при обнаружении
изменений в текущей локальной папке.
@param objects список имён элементов текущей локальной папки, которые были добавлены/удалены стронним или
нашем приложением.  */
    //@SuppressWarnings("unchecked")
    private void callbackOnCurrentFolderEvents (Object... ooo)
    {
        if (ooo != null && ooo.length > 0 && ooo[0] instanceof LocalWatchService.NasEvent)
        {
            //вместо того, чтобы добавлять/удалять новые строчки в локальную панель, мы просто
            // заново её заполняем как будто мы в неё только что перешли, но сперва убедимся,
            // что в списке есть изменения, которые произошли именно в текущей папке.
            for (Object o : ooo) {
                LocalWatchService.NasEvent e = (LocalWatchService.NasEvent) o;
                if (e.path.equals (strCurrentLocalPath)) {
                    Platform.runLater (()->applyStringAsNewLocalPath (strCurrentLocalPath));
                    break;
                }
            }
        }
    }

/** Обновляем содержиое TableView, на который указывает параметр.
@param ooo значения типа boolean. true указывает на локальный TableView, false — на удалённый. */
    private void callbackUpdateView (Object... ooo)
    {
        boolean local = (boolean) ooo[0];
        TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;

        if (tv.equals (tvClientSide))
            applyStringAsNewLocalPath (strCurrentLocalPath);
        else
            inlineReadRemoteFolder (strCurrentServerPath);
    }
//------------------------------------------ обработчики команд GUI -----------------------

/** Стандартное окно сообщения с кнопкой Ок.  */
    public static void messageBox (String header, String message, Alert.AlertType alerttype)
    {
        if (header == null) header = "";
        if (message == null) message = "";

        Alert a = new Alert (alerttype, message, ButtonType.CLOSE);
        a.setTitle (ALERT_TITLE);
        a.setHeaderText(header);

        String strFontSizeStyle = sformat(STYLE_FORMAT_SET_FONT_SIZE, fontSize);
        a.getDialogPane().setStyle(strFontSizeStyle);
        a.showAndWait();
    }

/** Стандартное окно сообщения с кнопками Да и Нет. */
    public static boolean messageBoxConfirmation (String header, String message, Alert.AlertType alerttype)
    {
        boolean boolOk = ANSWER_CANCEL;
        if (header == null) header = "";
        if (message == null) message = "";

        Alert a = new Alert(alerttype, message, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(header);
        a.setTitle (ALERT_TITLE);

        String strFontSizeStyle = sformat(STYLE_FORMAT_SET_FONT_SIZE, fontSize);
        a.getDialogPane().setStyle(strFontSizeStyle);

        Optional<ButtonType> option = a.showAndWait();
        if (option.isPresent() && option.get() == ButtonType.OK)
            boolOk = ANSWER_OK;

        return boolOk;
    }

    public static boolean itemNameStartsWithString (TableFileInfo t, String prefix)
    {
        String name = t.getFileName().toLowerCase(), prfx = prefix.toLowerCase();
        return name.startsWith(prfx);
    }

/** Меняем строку заголовка окна приложения в соответствии со статусом сетевого подключения.
    @param name указывает имя юзера, которое будет выводиться в заголовке окна. Если этот
    параметр NULL, в заголовке будет выводиться умолчальный текст. */
    private void updateMainWndTitleWithUserName (String name)
    {
        if (primaryStage != null) {
            String newtitle = MAINWND_TITLE;
            if (name != null)
                newtitle += " - вы вошли как : "+ name;
            primaryStage.setTitle (newtitle);
        }
    }

    private boolean showAuthentificationDialogWindow ()
    {
        try {
            if (dlgLoginController == null || dlgLoginStage == null) {
                // Загружаем fxml-файл и создаём новую сцену для всплывающего диалогового окна.
                FXMLLoader loader = new FXMLLoader();
                loader.setLocation (getClass().getResource ("/dlglogin.fxml"));
                VBox page = loader.load();
                setSceneFontSize(page, propMan.getDefaultFontSize());
                Scene scene = new Scene (page);

                // Создаём диалоговое окно Stage.
                dlgLoginStage = new Stage();
                dlgLoginStage.setTitle (MAINWND_TITLE + " - Авторизация пользователя");
                dlgLoginStage.initModality (Modality.WINDOW_MODAL);
                dlgLoginStage.initOwner (this.primaryStage);
                dlgLoginStage.setScene (scene);

                // Передаём адресата в контроллер.
                dlgLoginController = loader.getController();
                dlgLoginController.setDialogStage (dlgLoginStage);
            }
            // Отображаем диалоговое окно и ждём, пока пользователь его не закроет
            dlgLoginStage.showAndWait();
            return dlgLoginController.isButtnLoginPressed();
        }
        catch (IOException e) { e.printStackTrace(); }
        return false;
    }

    @FXML public void onactionMenuServer_Connection () {
        if (isConnected())
            disconnect();
        else
            onactionStartConnect (null);
    }

    @FXML public void onactionStartConnect (ActionEvent actionEvent)
    {
        if (showAuthentificationDialogWindow() && dlgLoginController != null)
            connectAndLogin();
    }

/** Создание папки на сервере в текущей папке.    */
    @FXML public void onactionMenuServer_CreateFolder (ActionEvent actionEvent)
    {
        String name = generateDefaultFolderName(tvServerSide);
        String errMsg = null;
        NasMsg nasMsg = netClient.create(name);

        if (nasMsg == null) {
            errMsg = ERROR_UNABLE_TO_PERFORM;
        }
        else if (nasMsg.opCode() == NM_OPCODE_ERROR) {
            errMsg = nasMsg.msg();
        }
        else if (nasMsg.opCode() == NM_OPCODE_OK) {
            addTvItemAsFolder (name, REMOTE);
        }
        if (errMsg != null)
            messageBox (ALERTHEADER_FOLDER_CREATION, errMsg, ERROR);
    }

/** Создание локальной папки в текущей папке. */
    @FXML public void onactionMenuClient_CreateFolder (ActionEvent actionEvent)
    {
        String name = generateDefaultFolderName (tvClientSide);
        Path pSubfolder = createSubfolder (Paths.get (strCurrentLocalPath), name);

        if (null == pSubfolder)
            messageBox (ALERTHEADER_FOLDER_CREATION, ERROR_UNABLE_TO_PERFORM, ERROR);
    }

/** в текущей клиентской папке удаляем выбранный элемент (файл или папку).    */
    @FXML public void onactionMenuClient_Delete (ActionEvent actionEvent)
    {
        TableFileInfo tfi = tvClientSide.getSelectionModel().getSelectedItem();
        //String errMsg = null;

        if (tfi != null)
            deleteLocalEntryByTfi (tfi);
        else
            messageBox(ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR);
    }

/** в текущей серверной папке удаляем выбранный элемент (файл или папку). */
    @FXML public void onactionMenuServer_Delete (ActionEvent actionEvent)
    {
        TableFileInfo tfi = tvServerSide.getSelectionModel().getSelectedItem();
        String errMsg = null;

        if (netClient == null) {
            errMsg = ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
        }
        else if (null == tfi) {
            errMsg = ERROR_UNABLE_TO_PERFORM;
        }
        else if (deleteRemoteEntryByIfi(tfi)) {
            deleteTvItem(tvServerSide, tfi);
            collectStatistics(tvServerSide);
        }
        if (errMsg != null)
            messageBox(ALERTHEADER_DELETION, errMsg, WARNING);

    }

/** Загрузка одного выбранного элемента-файла с сервера в текущую локальную папку */
    @FXML public void onactionMenu_Download (ActionEvent actionEvent)
    {
        TableFileInfo tfi;
        String errMsg = null;
        Alert.AlertType alertType = ERROR;

        if (netClient == null) {
            errMsg = ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
            alertType = WARNING;
        }
        else if (null != (tfi = tvServerSide.getSelectionModel().getSelectedItem()))
        {
            //загрузка папок (даже пустых) не реализована
            if (tfi.getFolder()) {
                errMsg = PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED;
                alertType = INFORMATION;
            }
            else errMsg = downloadFileByTfi (tfi);
        }
        if (errMsg != null)
            messageBox(ALERTHEADER_LOCAL2LOCAL, errMsg, alertType);
    }

/** Выгрузка одного выбранного элемента-файла из локальной папки на сервер в текущую папку    */
    @FXML public void onactionMenuClient_Upload (ActionEvent actionEvent)
    {
        TableFileInfo tfi;
        String errMsg = null;
        Alert.AlertType alertType = ERROR;

        if (netClient == null) {
            errMsg = ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
            alertType = WARNING;
        }
        else if (null != (tfi = tvClientSide.getSelectionModel().getSelectedItem())) {
            //загрузка папок (даже пустых) не реализована
            if (tfi.getFolder()) {
                errMsg = PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED;
                alertType = INFORMATION;
            }
            else  errMsg = uploadFileByTfi (tfi);
        }
        if (errMsg != null)
            messageBox(ALERTHEADER_LOCAL2SERVER, errMsg, alertType);
    }

/** В поле ввода textfieldCurrentPath_Client нажат ENTER. */
    @FXML public void onactionTextField_Client_ApplyAsPath (ActionEvent actionEvent) {
        changeLocalFolder (textfieldCurrentPath_Client.getText());
    }

/** В поле ввода textfieldCurrentPath_Server нажат ENTER. */
    @FXML public void onactionTextField_Server_ApplyAsPath (ActionEvent actionEvent)
    {
        String text = textfieldCurrentPath_Server.getText().trim();

        if (netClient == null) {   //перед подключением поле пути для сервера используется как поле ввода для имени пользователя
            //onCmdConnectAndLogin(text);
        }
        else  //если подключение уже установлено, то текст явялется папкой на сервере, которую нужно выбрать
            inlineReadRemoteFolder (text);
    }

    private void inlineReadRemoteFolder (String str)
    {
        NasMsg nm = netClient.goTo (str);
        if (!workUpAListRequestResult (nm))
            messageBox (ALERTHEADER_UNABLE_APPLY_AS_PATH, str, ERROR);
    }

/** Переходим в родительскую папку, если таковая существует.  */
    @FXML public void onactionButton_Client_LevelUp (ActionEvent actionEvent)
    {
        String strParent = stringPath2StringAbsoluteParentPath (strCurrentLocalPath);
        if (strParent != null) {
            if (strParent.isEmpty())
                messageBox (ALERTHEADER_LOCAL_STORAGE, sformat (PROMPT_FORMAT_ROOT_FOLDER, strCurrentLocalPath), INFORMATION);
            else
                changeLocalFolder (strParent);
        }
    }

/** Переходим в родительскую папку, если таковая существует.  */
    @FXML public void onactionButton_Server_LevelUp (ActionEvent actionEvent)
    {
        if (netClient != null) {
            String strParent = getParentFromRelative(userName, strCurrentServerPath);
            if (!sayNoToEmptyStrings(strParent)) strParent = userName;

            NasMsg nm = netClient.goTo(strParent);
            if (!workUpAListRequestResult (nm))
                messageBox(ALERTHEADER_UNABLE_APPLY_AS_PATH, strParent, ERROR);
        }
    }

/** Если в списке/таблице выбрана папка, заходим в неё.   */
    @FXML public void onactionMenuClient_OpenFolder (ActionEvent actionEvent) {
        openItemFolderOnClientSide();
    }

/** Если в списке/таблице выбрана папка, заходим в неё.   */
    @FXML public void onactionMenuServer_OpenFolder (ActionEvent actionEvent) {
        openItemFolderOnServerSide();
    }

/** двойной щелчок ЛКМ по пункту-папке открывает соттв. ему папку */
    @FXML public void tvOnMouseClickedClient (MouseEvent mouseEvent) {
        //TableView<TableFileInfo> tv = (TableView<TableFileInfo>) mouseEvent.getSource();
        if (mouseEvent.getClickCount() == 2)
            openItemFolderOnClientSide();
    }

    @FXML public void tvOnMouseClickedServer (MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2)
            openItemFolderOnServerSide();
    }

//-------------------------- гетеры и сетеры --------------------------------------------------------------------*/

    public TableView<TableFileInfo> getTvClient () { return tvClientSide; }

    public TableView<TableFileInfo> getTvServer () { return tvServerSide; }

    public NetClient getNetClient () { return netClient; }

//-------------------------- методы для работы с GUI ------------------------------------------------------------*/

    public String getStrCurrentLocalPath () { return strCurrentLocalPath; }

    public TableColumn<TableFileInfo, String> getColumnClientFileName () { return columnClientFileName; }

    public TableColumn<TableFileInfo, String> getColumnServerFileName () { return columnServerFileName; }

/** Заполняем таблицу элементами списка infolist. */
    void populateTableView (List<FileInfo> infolist, boolean local)
    {
        if (infolist != null) {
            long folders = 0L, files = 0L;
            TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;
            String strPrefix = local ? STR_PREFIX_LOCAL : STR_PREFIX_REMOTE;

            String s = SBAR_TEXT_FOLDER_READING_IN_PROGRESS;
            if (local)
                sbarSetText (s, null);
            else
                sbarSetText (null, s);
            enableUsersInput (DISABLE);

            Point p = populateTv (tv, infolist);
            folders = p.x;
            files = p.y;

            enableUsersInput (ENABLE);
            s = sformat (SBAR_TEXTFORMAT_FOLDER_STATISTICS, strPrefix, folders, files);
            if (local)
                sbarSetText (s, null);
            else
                sbarSetText (null, s);
        }
    }

/** Подсчитываем количества папок и файлов в указанной таблице и помещаем в строку состояния текст с полученной информацией.  */
    void collectStatistics (TableView<TableFileInfo> tv)
    {
        long folders = 0L, files = 0L;
        boolean local = tv == tvClientSide;
        String strPrefix = local ? STR_PREFIX_LOCAL : STR_PREFIX_REMOTE;

        String s = SBAR_TEXT_FOLDER_READING_IN_PROGRESS;
        if (local)
            sbarSetText (s, null);
        else
            sbarSetText (null, s);
        enableUsersInput(DISABLE);

        Point point = statisticsTv(tv);
        folders = point.x;
        files = point.y;

        enableUsersInput(ENABLE);
        s = sformat (SBAR_TEXTFORMAT_FOLDER_STATISTICS, strPrefix, folders, files);
        if (local)
            sbarSetText (s, null);
        else
            sbarSetText (null, s);
    }

/** Формируем текст, который будет по умолчанию отображаться в строке состояния. Левая часть отображает состояние клиента,
 правая — сервера. В качестве любого из параметров можно передать null, если соответствующую подстроку изменять не нужно. */
    void sbarSetText (String local, String server) {
        if (local != null)
            sbarLocalStatistics = local;
        if (server != null)
            sbarServerStatistics = server;
        sbarTextDefault = sformat (SBAR_TEXTFORMAT_STATISTICS, sbarLocalStatistics, sbarServerStatistics);
        textStatusBar.setText(sbarTextDefault);
    }

//------------------- Вспомогательные методы. Фактически, — поименованные куски кода. ---------------------------*/

/** Запрещаем или разрешаем использование элементов управления. (Используется во время выполнения длительных операций.)   */
    void enableUsersInput (boolean enable)
    {
        textfieldCurrentPath_Client.setDisable(!enable);
        textfieldCurrentPath_Server.setDisable(!enable);
        buttonDownload.setDisable(!enable && netClient != null);
        buttonUpload.setDisable(!enable && netClient != null);
        buttonLevelUp_Client.setDisable(!enable);
        buttonLevelUp_Server.setDisable(!enable);
        tvClientSide.setDisable(!enable);
        tvServerSide.setDisable(!enable);
    }

/** разбираем результат запроса LIST<br>
nm.msg    = относительный путь в папке юзера на сервере, или текст сообщения об ошибке.
nm.data   = список содержимого этой папки (если нет ошибки).
 @return true если nm.opCode() == OK. С остальным пусть разбирается вызывающая функция.    */
    //@SuppressWarnings ("unchecked")
    private boolean workUpAListRequestResult (NasMsg nm)
    {
        boolean ok = false;
        if (nm != null && nm.opCode() == NM_OPCODE_OK) {
            ok = true;
            List<FileInfo> infolist = (List<FileInfo>) nm.data();
            if (infolist != null) {
                populateTableView (infolist, REMOTE);
                strCurrentServerPath = nm.msg();
                textfieldCurrentPath_Server.setText(nm.msg());
            }
            else lnprint("Не удалось вывести список содержимого папки.");
        }
        return ok;
    }

/*
    callbackOnCurrentFolderEvents()
        onactionTextField_Client_ApplyAsPath()  //-
        openItemFolderOnClientSide()    //-
    downloadFileByTfi
        readUserSpecificProperties()    //-
    changeLocalFolder()
*/
/** считая указанную строку путём к каталогу, пытаемся отобразить содержимое этого каталога в «клиентской» панели.    */
    private boolean applyStringAsNewLocalPath (String strPath)
    {
        boolean ok = false;
        if (!sayNoToEmptyStrings (strPath))
            strPath = System.getProperty (STR_DEF_FOLDER);  //< чтобы не бросаться исключениями, сменим папку на умолчальную

        if (isStringOfRealPath (strPath)) {
            strCurrentLocalPath = strPath;
            List<FileInfo> infolist = listFolderContents (strCurrentLocalPath);
            populateTableView (infolist, LOCAL);
            ok = true;
        }
        else messageBox (PROMPT_DIR_ENTRY_DOESNOT_EXISTS, strPath, WARNING);
        return ok;
    }

/** Открывает локальную папку, которая соответствует выбранному пункту в таблице — делаем эту папку
    текущей локальной папкой.  */
    void openItemFolderOnClientSide ()
    {
        TableFileInfo tfi = tvClientSide.getSelectionModel().getSelectedItem();
        if (tfi != null && tfi.getFolder())
            changeLocalFolder (Paths.get (strCurrentLocalPath, tfi.getFileName()).toString());
    }

/** Открывает удалённую папку, которая соответствует выбранному пункту в таблице — делаем эту папку
    текущей удалённой папкой. */
    void openItemFolderOnServerSide ()
    {
        TableFileInfo tfi = tvServerSide.getSelectionModel().getSelectedItem();
        if (tfi != null && tfi.getFolder() && netClient != null)
        {
            String strFolderName = tfi.getFileName();
            NasMsg nm = netClient.goTo (strCurrentServerPath, strFolderName);

            if (!workUpAListRequestResult (nm))
                messageBox (ALERTHEADER_UNABLE_APPLY_AS_PATH, strFolderName, ERROR);
        }
    }

/** Добавляем в TableView пункт-папку. */
    private void addTvItemAsFolder (@NotNull String name, boolean local) {
        if (tableViewManager != null) {
            TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;
            addItemAsFolder (name, tv);
            collectStatistics(tv);
        }
    }

/** генерируем имя для новой папки примерно так же, как это делаем Windows.   */
    private String generateDefaultFolderName (TableView<TableFileInfo> tv) {
        ObservableList<TableFileInfo> list = tv.getItems();

        //составляем список папок, чьи имена начинаются на NEW_FOLDER_NAME
        List<String> strsublist = list.stream()
            .filter(t->t.getFolder() && itemNameStartsWithString(t, NEW_FOLDER_NAME))
            .map(TableFileInfo::getFileName)
            .collect(Collectors.toList());

        //добавляем к NEW_FOLDER_NAME номер, чтобы оно отличалось от имён отсальных папок
        String name = NEW_FOLDER_NAME;
        if (!strsublist.isEmpty()) {
            for (int i = 1; strsublist.contains(name); i++) {
                name = sformat ("%s (%d)", NEW_FOLDER_NAME, i);
            }
        }
        return name;
    }

    private boolean confirmEntryDeletion (String folderName, long entries) {
        boolean canDelete = false;
        if (entries > 0L) {
            String s = sformat (PROMPT_FORMAT_FOLDER_DELETION_CONFIRMATION, folderName);
            canDelete = messageBoxConfirmation (ALERTHEADER_DELETION, s, CONFIRMATION);
        }
        else { canDelete = entries == 0L; }
        return canDelete;
    }

/** удаляем элемент текущего локального каталога, на который указывает елемент таблицы tfi.   */
    private boolean deleteLocalEntryByTfi (@NotNull TableFileInfo tfi)
    {
        FileInfo fi = tfi.toFileInfo();
        Path paim = Paths.get (strCurrentLocalPath, fi.getFileName());

        boolean ok = false;
        boolean error = false;
        boolean dir = fi.isDirectory();
        long entries = -1L;

        if (dir) {
            entries = countDirectoryEntries (paim);
            error = entries < 0L;
            ok = !error && (entries == 0L || confirmEntryDeletion (paim.toString(), entries));
        }
        else { ok = confirmFileDeletion (paim.toString()); }

        if (ok) {
            ok = deleteFileOrDirectory (paim);
            //try
            //{
            //    if (lockOnWatching.tryLock(1000, MILLISECONDS))
            //    {
            //        lockOnWatching.unlock();
            //    }
            //}
            //catch (InterruptedException e){e.printStackTrace();}
            error = !ok;
        }
        if (error) messageBox (ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR);
        return ok;
    }

/** удаляем элемент текущего удалённого каталога, на который указывает елемент таблицы tfi.   */
    private boolean deleteRemoteEntryByIfi (@NotNull TableFileInfo tfi)
    {
        FileInfo fi = tfi.toFileInfo();
        String paim = Paths.get(strCurrentServerPath, fi.getFileName()).toString();

        boolean ok = false;
        boolean error = false;
        boolean dir = fi.isDirectory();
        long entries = -1L;

        if (dir) {
            entries = netClient.countFolderEntries (strCurrentServerPath, fi);
            error = entries < 0L;
            ok = !error && (entries == 0L || confirmEntryDeletion (paim, entries));
        }
        else { ok = confirmFileDeletion(paim); }

        if (ok) {
            OperationCodes opcode = netClient.delete (strCurrentServerPath, fi).opCode();
            ok = opcode == NM_OPCODE_OK;
            error = opcode == NM_OPCODE_ERROR;
        }
        if (error) messageBox(ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR);
        return ok;
    }

    private boolean confirmFileDeletion (String strFilePath)
    {
        String s = sformat (PROMPT_FORMAT_FILE_DELETION_CONFIRMATION, strFilePath);
        return ANSWER_OK == messageBoxConfirmation(ALERTHEADER_DELETION, s, CONFIRMATION);
    }

/** загружаем файл с сервера в текущую локальную папку; если файл уже существует, запрашиваем
подтверждение пользователя. */
    private String downloadFileByTfi (TableFileInfo tfi)
    {
        String strTarget = Paths.get (strCurrentLocalPath, tfi.getFileName()).toString();
        String strErr = null;
        if (!isStringOfRealPath (strTarget) || isItSafeToReplaceFile (strTarget))
        {
    /* При скачивании файла в локальную папку создаётся временный каталог, потом из него
       переносится файл, потом временный каталог удаляется… Служба наблюдения не должна
       всё это видеть… */
            strErr = new UnwatchableExecutor<String>().execute (()->{
                String result = ERROR_UNABLE_TO_PERFORM;
                NasMsg nm = netClient.download (strCurrentLocalPath, strCurrentServerPath, tfi.toFileInfo());
                if (nm != null) {
                    if (nm.opCode() == NM_OPCODE_OK) {
                        applyStringAsNewLocalPath (strCurrentLocalPath);
                        result = null;
                    }
                    else if (nm.opCode() == NM_OPCODE_ERROR  &&  nm.msg() != null)
                        result = nm.msg();
                }
                return result;
            });
        }
        return strErr;
    }

/** проверяем, существует ли файл и, если существует, запрашиваем у юзера подтверждение на перезапись   */
    private boolean isItSafeToReplaceFile (String strTarget)
    {
        String s = sformat (PROMPT_FORMAT_REPLACE_CONFIRMATION, strTarget);
        return ANSWER_OK == messageBoxConfirmation (ALERTHEADER_DOWNLOADING, s,
                                                    Alert.AlertType.CONFIRMATION);
    }

/** выгружаем файл на сервер в текущую удалённую папку; если файл уже существует, запрашиваем подтверждение пользователя.    */
    private String uploadFileByTfi (TableFileInfo tfi) {

        String strTargetName = tfi.getFileName();
        String strErr = ERROR_UNABLE_TO_PERFORM;
        FileInfo fi = netClient.fileInfo (strCurrentServerPath, strTargetName);

        if (fi != null) {
            if (fi.isDirectory()) {
                messageBox (ALERTHEADER_LOCAL2SERVER, PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED, WARNING);
                strErr = null;
            }
            else if (!isReadable (Paths.get (strCurrentLocalPath, fi.getFileName()))) {
                messageBox (ALERTHEADER_LOCAL2SERVER, PROMPT_FILE_IS_NOT_ACCESSIBLE, WARNING);
                strErr = null;
            }
            else if (fi.isExists() && !isItSafeToUploadFile (strTargetName))
                strErr = null;
            else {
                NasMsg nm = netClient.upload (strCurrentLocalPath, strCurrentServerPath, tfi.toFileInfo());
                if (nm != null) {
                    if (workUpAListRequestResult (netClient.list (strCurrentServerPath)))
                        strErr = null;
                    else
                    if (nm.opCode() == NM_OPCODE_ERROR && nm.msg() != null)
                        strErr = nm.msg();
                }
            }
        }
        return strErr;
    }

/** проверяем, существует ли файл и, если существует, запрашиваем у юзера подтверждение на перезапись   */
    private boolean isItSafeToUploadFile (String strTargetName)
    {
        String strPath = Paths.get (strCurrentServerPath, strTargetName).toString();
        String s = sformat (PROMPT_FORMAT_REPLACE_CONFIRMATION, strPath);

        return ANSWER_OK == messageBoxConfirmation (ALERTHEADER_UPLOADING, s,
                                                    Alert.AlertType.CONFIRMATION);
    }

/** Устанавливаем размер шрифта (в пикселах) для окна.    */
    private void setSceneFontSize (VBox vbox, int size) {
        size = Math.max(size, MIN_FONT_SIZE);
        size = Math.min(size, MAX_FONT_SIZE);
        fontSize = size;

        String strFontSizeStyle = sformat(STYLE_FORMAT_SET_FONT_SIZE, size);
        vbox.setStyle(strFontSizeStyle);
    }

/** Переключение некоторых элементов GUI в соответствии со статусом сетевого подключения. */
    private void swapControlsOnConnect (boolean connected)
    {
        buttonConnect.setManaged (!connected);
        buttonConnect.setVisible (!connected);
        textfieldCurrentPath_Server.setVisible (connected);
        textfieldCurrentPath_Server.setManaged (connected);
    }

/** Считываем настройки приложения, специфичные для подключенного юзера. */
    private void readUserSpecificProperties (String userName)
    {
        if (propMan != null) {
            fontSize = propMan.getUserFontSize (userName);
            setSceneFontSize (rootbox, fontSize);

            strCurrentLocalPath = propMan.getUserLocalPath (userName);
            strCurrentServerPath = propMan.getUserRemotePath (userName);
        }
        else {
            strCurrentServerPath = userName;
            strCurrentLocalPath = System.getProperty (STR_DEF_FOLDER);
        }
        changeLocalFolder (strCurrentLocalPath);
    }

/**    Изменяем текущую локальную папку в соотв-вии с параметром strNew. (Синхронизация используется для того, чтобы
 служба слежения за каталогом не обрабатывала изменения в прежнем каталоге, если таковые случатся во время
 работы этого метода.)    */
    private void changeLocalFolder (String strNew)
    {
        /* Вроде бы не нужно всё так усложнять при простом переключении на новую папку, но во время
           считывания содержимого новой папки какие-то события могут произойти в прежней папке, что
           заставит службу наблюдения затеять обновление TableView для прежней папки… Проще немного
           перестраховаться. */
        new UnwatchableExecutor<Void>().execute (()->{
            if (applyStringAsNewLocalPath (strNew))
                textfieldCurrentPath_Client.setText (strCurrentLocalPath);
            return null;
        });
    }

/** Класс предназначен для операций, для выполнения которых нужно приостановливать службу наблюдения
    за текущим каталогом.<p>
    В конструкторе приостанавливаем службу, в абстрактном методе выполняем какие-то действия, а
    в close() снова запускаем службу наблюдения за текущим каталогом.<p>
    <b>Причина использования именно такого подхода.</b><p>
    Во время выполнения некоторых действий текущий локальный каталог или его содержимое могут
    измениться. Порою эти изменения носят т.с. служебный характер, а служба наблюдения, регистрируя
    их, мешает работе приложения, которому и без этого хватает геморроя в лице JavaFX…<p>
    Использоваие замков решает проблему, но выглядит как-то аляповато. Пробуем сделать красиво… */
    private class UnwatchableExecutor<T>
    {
        public T execute (Supplier<T> supplier) {
            clientWatcher.suspendWatching();
            T t = supplier.get();
            clientWatcher.resumeWatching (strCurrentLocalPath);
            return t;
        }
    }

    public boolean isConnected () { return netClient != null  && netClient.isConnected(); }

    public boolean isLocalView (TableView<TableFileInfo> tv) { return tv.equals (tvClientSide); }
}
