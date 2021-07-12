package ru.gb.simplenas.client;

import com.sun.istack.internal.NotNull;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
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
import ru.gb.simplenas.client.structs.TableFileInfo;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;
import ru.gb.simplenas.common.structs.OperationCodes;

import java.awt.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javafx.scene.control.Alert.AlertType.ERROR;
import static javafx.scene.control.Alert.AlertType.*;
import static ru.gb.simplenas.client.CFactory.*;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.services.impl.NasFileManager.getParentFromRelative;
import static ru.gb.simplenas.common.structs.OperationCodes.*;

public class Controller implements Initializable {
    private static final Logger LOGGER = LogManager.getLogger(Controller.class.getName());
    private final Object syncObj = new Object();
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
    @FXML public TableView<TableFileInfo> tvServerSide;
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
    private String strCurrentLocalPath = STR_EMPTY;
    private String strCurrentServerPath = STR_EMPTY;
    private String sbarTextDefault = STR_EMPTY;
    private String sbarLocalStatistics = STR_EMPTY;
    private String sbarServerStatistics = STR_EMPTY;
    private Thread javafx;
    private boolean extraInitialisationIsDone = false;
    private TableViewManager tableViewManager;
    private ClientPropertyManager propMan;
    private ClientWatchService clientWatcher;
    private Lock lockOnWatching;


    //------------------------------- инициализация и завершение ----------------------------------------------------*/

    public static void messageBox (String header, String message, Alert.AlertType alerttype) {
        if (header == null) { header = ""; }
        if (message == null) { message = ""; }

        Alert a = new Alert(alerttype, message, ButtonType.CLOSE);
        a.setTitle(CFactory.ALERT_TITLE);
        a.setHeaderText(header);
        a.showAndWait();
    }

    public static boolean messageBoxConfirmation (String header, String message, Alert.AlertType alerttype) {
        boolean boolOk = ANSWER_CANCEL;
        if (header == null) { header = ""; }
        if (message == null) { message = ""; }

        Alert a = new Alert(alerttype, message, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(header);
        a.setTitle(CFactory.ALERT_TITLE);

        Optional<ButtonType> option = a.showAndWait();
        if (option.isPresent() && option.get() == ButtonType.OK) {
            boolOk = ANSWER_OK;
        }
        return boolOk;
    }

    public static boolean itemNameStartsWithString (TableFileInfo t, String prefix) {
        String name = t.getFileName().toLowerCase(), prfx = prefix.toLowerCase();
        return name.startsWith(prfx);
    }

    @Override public void initialize (URL location, ResourceBundle resources) {
        javafx = Thread.currentThread();

        propMan = getProperyManager();
        strCurrentLocalPath = propMan.getLastLocalPathString();

        lockOnWatching = new ReentrantLock();
        clientWatcher = new LocalWatchService((ooo)->callbackOnCurrentFolderEvents(), lockOnWatching);
        clientWatcher.startWatchingOnFolder(strCurrentLocalPath);

        sbarSetDefaultText(STR_EMPTY, SBAR_TEXT_SERVER_NOCONNECTION);

        tableViewManager = new TableViewManager(this);
        textfieldCurrentPath_Client.setText(strCurrentLocalPath);
        textfieldCurrentPath_Server.setPromptText(TEXTFIELD_SERVER_PROMPTTEXT_DOCONNECT);
        populateTableView(listFolderContents(strCurrentLocalPath), LOCAL);

        setContextMenuEventHandler_OnShoing(menuClientTableActions, tvClientSide);
        setContextMenuEventHandler_OnShoing(menuServerTableActions, tvServerSide);

        extraInitialisationIsDone = false;
        enableUsersInput(ENABLE);
    }

    void onCmdConnectAndLogin (String name) {
        if (sayNoToEmptyStrings(name)) {
            if (!extraInitialisationIsDone) {
                extraInitialisationIsDone = true;
            }
            if (connect()) {
                login(name);
            }
            else { messageBox(CFactory.ALERTHEADER_CONNECTION, ERROR_UNABLE_CONNECT_TO_SERVER, WARNING); }
        }
    }

    private boolean connect () {
        boolean result = false;
        if (propMan != null) {
            netClient = newNetClient((ooo)->callbackOnNetClientDisconnection(), propMan.getRemotePort(), propMan.getHostString());
            result = netClient.connect();
            if (!result) {
                netClient = null;
            }
        }
        return result;
    }

    private void login (String name) {
        NasMsg nm = null;
        if (netClient == null || (null == (nm = netClient.login(name)))) {
            messageBox(ALERTHEADER_AUTHENTIFICATION, ERROR_UNABLE_TO_PERFORM, ERROR);
        }
        else if (nm.opCode() == OK) {
            userName = name;
            updateControlsOnSuccessfulLogin();
        }
        else if (nm.opCode() == OperationCodes.ERROR) {
            String strErr = nm.msg();
            if (!sayNoToEmptyStrings(strErr)) {
                strErr = ERROR_UNABLE_TO_PERFORM;
            }
            messageBox(ALERTHEADER_AUTHENTIFICATION, strErr, WARNING);
        }
    }

    private void updateControlsOnSuccessfulLogin () {
        textfieldCurrentPath_Server.clear();
        textfieldCurrentPath_Server.setText(STR_EMPTY);
        textfieldCurrentPath_Server.setPromptText(TEXTFIELD_SERVER_PROMPTTEXT_LOGGEDIN);
        sbarSetDefaultText(null, SBAR_TEXT_SERVER_ONAIR);

        messageBox(ALERTHEADER_AUTHENTIFICATION, String.format(STRFORMAT_YOUARE_LOGGEDIN, userName), INFORMATION);

        if (propMan != null) { strCurrentServerPath = propMan.getLastRemotePathString(); }

        String strRemotePath = Paths.get(userName, strCurrentServerPath).toString();
        if (!workUpAListRequestResult(netClient.list(strRemotePath))) {
            messageBox(ALERTHEADER_REMOUTE_STORAGE, String.format(PROMPT_FORMAT_UNABLE_LIST, userName), ERROR);
        }
        updateMainWndTitleWithUserName();
    }

    void closeSession () {
        storeProperties();
        if (netClient != null) {
            netClient.disconnect();
        }
        if (clientWatcher != null) {
            clientWatcher.close();
        }
    }

    private void storeProperties () {
        if (propMan != null) {
            propMan.setLastLocalPathString(strCurrentLocalPath);
            if (sayNoToEmptyStrings(strCurrentServerPath)) {
                propMan.setLastRemotePathString(relativizeByFolderName(userName, strCurrentServerPath));
            }
            propMan.close();
        }
    }

    void onMainWndShowing (Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    //------------------------------------------ обработчики команд GUI ---------------------------------------------*/

    private void callbackOnNetClientDisconnection () {
        netClient = null;
        userName = null;
        sbarSetDefaultText(null, CFactory.SBAR_TEXT_SERVER_NOCONNECTION);
    }

    private void callbackOnCurrentFolderEvents () {
        applyStringAsNewLocalPath(strCurrentLocalPath);
    }

    private void updateMainWndTitleWithUserName () {
        if (primaryStage != null) {
            String newtitle = String.format("%s - вы вошли как : %s", MAINWND_TITLE, sayNoToEmptyStrings(userName) ? userName : CFactory.NO_USER_TITLE);
            primaryStage.setTitle(newtitle);
        }
    }

    @FXML public void onactionMenuServer_CreateFolder (ActionEvent actionEvent) {
        String name = generateDefaultFolderName(tvServerSide);
        String errMsg = null;
        NasMsg nasMsg = netClient.create(name);

        if (nasMsg == null) {
            errMsg = ERROR_UNABLE_TO_PERFORM;
        }
        else if (nasMsg.opCode() == OperationCodes.ERROR) {
            errMsg = nasMsg.msg();
        }
        else if (nasMsg.opCode() == OK) {
            addTvItemAsFolder(name, REMOTE);
        }
        if (errMsg != null) { messageBox(ALERTHEADER_FOLDER_CREATION, errMsg, ERROR); }
    }

    @FXML public void onactionMenuClient_CreateFolder (ActionEvent actionEvent) {
        String name = generateDefaultFolderName(tvClientSide);
        Path pSubfolder = createSubfolder(Paths.get(strCurrentLocalPath), name);

        if (null == pSubfolder) { messageBox(ALERTHEADER_FOLDER_CREATION, ERROR_UNABLE_TO_PERFORM, ERROR); }
    }

    @FXML public void onactionMenuClient_Delete (ActionEvent actionEvent) {
        TableFileInfo tfi = tvClientSide.getSelectionModel().getSelectedItem();
        String errMsg = null;

        if (tfi != null) { deleteLocalEntryByTfi(tfi); }
        else { messageBox(ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR); }
    }

    @FXML public void onactionMenuServer_Delete (ActionEvent actionEvent) {
        TableFileInfo tfi = tvServerSide.getSelectionModel().getSelectedItem();
        String errMsg = null;

        if (netClient == null) {
            errMsg = CFactory.ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
        }
        else if (null == tfi) {
            errMsg = ERROR_UNABLE_TO_PERFORM;
        }
        else if (deleteRemoteEntryByIfi(tfi)) {
            deleteTvItem(tvServerSide, tfi);
            collectStatistics(tvServerSide);
        }
        if (errMsg != null) { messageBox(ALERTHEADER_DELETION, errMsg, WARNING); }

    }

    @FXML public void onactionMenu_Download (ActionEvent actionEvent) {
        TableFileInfo tfi;
        String errMsg = null;
        Alert.AlertType alertType = ERROR;

        if (netClient == null) {
            errMsg = CFactory.ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
            alertType = WARNING;
        }
        else if (null != (tfi = tvServerSide.getSelectionModel().getSelectedItem())) {
            if (tfi.getFolder()) {
                errMsg = PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED;
                alertType = INFORMATION;
            }
            else { errMsg = downloadFileByTfi(tfi); }
        }
        if (errMsg != null) { messageBox(ALERTHEADER_LOCAL2LOCAL, errMsg, alertType); }
    }

    @FXML public void onactionMenuClient_Upload (ActionEvent actionEvent) {
        TableFileInfo tfi;
        String errMsg = null;
        Alert.AlertType alertType = ERROR;

        if (netClient == null) {
            errMsg = ERROR_NO_CONNECTION_TO_REMOTE_STORAGE;
            alertType = WARNING;
        }
        else if (null != (tfi = tvClientSide.getSelectionModel().getSelectedItem())) {
            if (tfi.getFolder()) {
                errMsg = PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED;
                alertType = INFORMATION;
            }
            else { errMsg = uploadFileByTfi(tfi); }
        }
        if (errMsg != null) { messageBox(ALERTHEADER_LOCAL2SERVER, errMsg, alertType); }
    }

    @FXML public void onactionTextField_Client_ApplyAsPath (ActionEvent actionEvent) {
        lockOnWatching.lock();
        {
            applyStringAsNewLocalPath(textfieldCurrentPath_Client.getText());
            clientWatcher.startWatchingOnFolder(strCurrentLocalPath);
        }
        lockOnWatching.unlock();
    }

    @FXML public void onactionTextField_Server_ApplyAsPath (ActionEvent actionEvent) {
        String text = textfieldCurrentPath_Server.getText().trim();

        if (netClient == null) {
            onCmdConnectAndLogin(text);
        }
        else {
            NasMsg nm = netClient.goTo(text);
            if (!workUpAListRequestResult(nm)) {
                messageBox(ALERTHEADER_UNABLE_APPLY_AS_PATH, text, ERROR);
            }
        }
    }

    @FXML public void onactionButton_Client_LevelUp (ActionEvent actionEvent) {
        String strParent = stringPath2StringAbsoluteParentPath(strCurrentLocalPath);

        if (strParent != null) {
            if (strParent.isEmpty()) {
                messageBox(ALERTHEADER_LOCAL_STORAGE, sformat(PROMPT_FORMAT_ROOT_FOLDER, strCurrentLocalPath), INFORMATION);
            }
            else if (tryLock(1000, MILLISECONDS)) {
                populateTableView(listFolderContents(strParent), LOCAL);
                strCurrentLocalPath = strParent;
                textfieldCurrentPath_Client.setText(strCurrentLocalPath);
                clientWatcher.startWatchingOnFolder(strCurrentLocalPath);
                lockOnWatching.unlock();
            }
        }
    }

    @FXML public void onactionButton_Server_LevelUp (ActionEvent actionEvent) {
        if (netClient != null) {
            String strParent = getParentFromRelative(userName, strCurrentServerPath);
            if (!sayNoToEmptyStrings(strParent)) { strParent = userName; }

            NasMsg nm = netClient.goTo(strParent);
            if (nm == null || !workUpAListRequestResult(nm)) {
                messageBox(ALERTHEADER_UNABLE_APPLY_AS_PATH, strParent, ERROR);
            }
        }
    }

    @FXML public void onactionMenuClient_OpenFolder (ActionEvent actionEvent) {
        openFolderOnClientSide();
    }

    //-------------------------- гетеры и сетеры --------------------------------------------------------------------*/

    @FXML public void onactionMenuServer_OpenFolder (ActionEvent actionEvent) {
        openFolderOnServerSide();
    }

    @FXML public void tvOnMouseClickedClient (MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
            openFolderOnClientSide();
        }
    }

    @FXML public void tvOnMouseClickedServer (MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) { openFolderOnServerSide(); }
    }

    public TableView<TableFileInfo> getTvClient () { return tvClientSide; }

    public TableView<TableFileInfo> getTvServer () { return tvServerSide; }

    public NetClient getNetClient () { return netClient; }

    //-------------------------- методы для работы с GUI ------------------------------------------------------------*/

    public String getStrCurrentLocalPath () { return strCurrentLocalPath; }

    public TableColumn<TableFileInfo, String> getColumnClientFileName () { return columnClientFileName; }

    public TableColumn<TableFileInfo, String> getColumnServerFileName () { return columnServerFileName; }

    void populateTableView (@NotNull List<FileInfo> infolist, boolean local) {
        if (infolist != null) {
            long folders = 0L, files = 0L;
            TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;
            String strPrefix = local ? STR_PREFIX_LOCAL : STR_PREFIX_REMOTE;

            String s = SBAR_TEXT_FOLDER_READING_IN_PROGRESS;
            if (local) { sbarSetDefaultText(s, null); }
            else { sbarSetDefaultText(null, s); }
            enableUsersInput(DISABLE);

            Point p = populateTv(tv, infolist);
            folders = p.x;
            files = p.y;

            enableUsersInput(ENABLE);
            s = String.format(SBAR_TEXTFORMAT_FOLDER_STATISTICS, strPrefix, folders, files);
            if (local) { sbarSetDefaultText(s, null); }
            else { sbarSetDefaultText(null, s); }
        }
    }

    void collectStatistics (TableView<TableFileInfo> tv) {
        long folders = 0L, files = 0L;
        boolean local = tv == tvClientSide;
        String strPrefix = local ? STR_PREFIX_LOCAL : STR_PREFIX_REMOTE;

        String s = CFactory.SBAR_TEXT_FOLDER_READING_IN_PROGRESS;
        if (local) { sbarSetDefaultText(s, null); }
        else { sbarSetDefaultText(null, s); }
        enableUsersInput(DISABLE);

        Point point = statisticsTv(tv);
        folders = point.x;
        files = point.y;

        enableUsersInput(ENABLE);
        s = String.format(CFactory.SBAR_TEXTFORMAT_FOLDER_STATISTICS, strPrefix, folders, files);
        if (local) { sbarSetDefaultText(s, null); }
        else { sbarSetDefaultText(null, s); }
    }

    void sbarSetDefaultText (String local, String server) {
        if (local != null) { sbarLocalStatistics = local; }
        if (server != null) { sbarServerStatistics = server; }
        sbarTextDefault = String.format(CFactory.SBAR_TEXTFORMAT_STATISTICS, sbarLocalStatistics, sbarServerStatistics);
        textStatusBar.setText(sbarTextDefault);
    }

    //------------------- Вспомогательные методы. Фактически, — поименованные куски кода. ---------------------------*/

    void enableUsersInput (boolean enable) {
        textfieldCurrentPath_Client.setDisable(!enable);
        textfieldCurrentPath_Server.setDisable(!enable);
        buttonDownload.setDisable(!enable && netClient != null);
        buttonUpload.setDisable(!enable && netClient != null);
        buttonLevelUp_Client.setDisable(!enable);
        buttonLevelUp_Server.setDisable(!enable);
        tvClientSide.setDisable(!enable);
        tvServerSide.setDisable(!enable);
    }

    @SuppressWarnings ("unchecked") private boolean workUpAListRequestResult (NasMsg nm) {
        boolean ok = false;
        if (nm != null && nm.opCode() == OK) {
            ok = true;
            List<FileInfo> infolist = (List<FileInfo>) nm.data();
            if (infolist != null) {
                populateTableView(infolist, REMOTE);
                strCurrentServerPath = nm.msg();
                textfieldCurrentPath_Server.setText(nm.msg());
            }
            else { lnprint("Не удалось вывести список содержимого папки."); }
        }
        return ok;
    }

    void applyStringAsNewLocalPath (String strPath) {
        if (!sayNoToEmptyStrings(strPath)) { strPath = System.getProperty(STR_DEF_FOLDER); }

        if (isStringOfRealPath(strPath)) {
            strCurrentLocalPath = strPath;
            List<FileInfo> infolist = listFolderContents(strCurrentLocalPath);
            populateTableView(infolist, LOCAL);
        }
        else { messageBox(CFactory.PROMPT_DIR_ENTRY_DOESNOT_EXISTS, strPath, WARNING); }
    }

    void openFolderOnClientSide () {
        TableFileInfo tfi = tvClientSide.getSelectionModel().getSelectedItem();
        if (tfi != null && tfi.getFolder()) {
            lockOnWatching.lock();
            {
                String strPath = Paths.get(strCurrentLocalPath, tfi.getFileName()).toString();
                applyStringAsNewLocalPath(strPath);
                textfieldCurrentPath_Client.setText(strPath);
                clientWatcher.startWatchingOnFolder(strCurrentLocalPath);
            }
            lockOnWatching.unlock();
        }
    }

    void openFolderOnServerSide () {
        TableFileInfo tfi = tvServerSide.getSelectionModel().getSelectedItem();
        if (tfi != null && tfi.getFolder() && netClient != null) {
            String strFolderName = tfi.getFileName();
            NasMsg nm = netClient.goTo(strCurrentServerPath, strFolderName);
            if (!workUpAListRequestResult(nm)) {
                messageBox(ALERTHEADER_UNABLE_APPLY_AS_PATH, strFolderName, ERROR);
            }
        }
    }

    private void addTvItemAsFolder (@NotNull String name, boolean local) {
        if (tableViewManager != null) {
            TableView<TableFileInfo> tv = local ? tvClientSide : tvServerSide;
            CFactory.addItemAsFolder(name, tv);
            collectStatistics(tv);
        }
    }

    private String generateDefaultFolderName (TableView<TableFileInfo> tv) {
        ObservableList<TableFileInfo> list = tv.getItems();

        List<String> strsublist = list.stream().filter(t->t.getFolder() && itemNameStartsWithString(t, CFactory.NEW_FOLDER_NAME)).map(TableFileInfo::getFileName).collect(Collectors.toList());

        String name = CFactory.NEW_FOLDER_NAME;
        if (!strsublist.isEmpty()) {
            for (int i = 1; strsublist.contains(name); i++) {
                name = String.format("%s (%d)", CFactory.NEW_FOLDER_NAME, i);
            }
        }
        return name;
    }

    private boolean confirmEntryDeletion (String folderName, int entries) {
        boolean canDelete = false;
        if (entries > 0) {
            canDelete = messageBoxConfirmation(ALERTHEADER_DELETION, sformat(PROMPT_FORMAT_FOLDER_DELETION_CONFIRMATION, folderName), CONFIRMATION);
        }
        else { canDelete = entries == 0; }
        return canDelete;
    }

    private boolean deleteLocalEntryByTfi (@NotNull TableFileInfo tfi) {
        FileInfo fi = tfi.toFileInfo();
        Path paim = Paths.get(strCurrentLocalPath, fi.getFileName());

        boolean ok = false;
        boolean error = false;
        boolean dir = fi.isDirectory();
        int entries = -1;

        if (dir) {
            entries = countDirectoryEntries(paim);
            error = entries < 0;
            ok = !error && (entries == 0 || confirmEntryDeletion(paim.toString(), entries));
        }
        else { ok = confirmFileDeletion(paim.toString()); }

        if (ok) {
            ok = deleteFileOrDirectory(paim);
            if (tryLock(1000, MILLISECONDS)) { lockOnWatching.unlock(); }
            error = !ok;
        }
        if (error) { messageBox(ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR); }
        return ok;
    }

    private boolean deleteRemoteEntryByIfi (@NotNull TableFileInfo tfi) {
        FileInfo fi = tfi.toFileInfo();
        String paim = Paths.get(strCurrentServerPath, fi.getFileName()).toString();

        boolean ok = false;
        boolean error = false;
        boolean dir = fi.isDirectory();
        int entries = -1;

        if (dir) {
            entries = netClient.countFolderEntries(strCurrentServerPath, fi);
            error = entries < 0;
            ok = !error && (entries == 0 || confirmEntryDeletion(paim, entries));
        }
        else { ok = confirmFileDeletion(paim); }

        if (ok) {
            OperationCodes opcode = netClient.delete(strCurrentServerPath, fi).opCode();
            ok = opcode == OK;
            error = opcode == OperationCodes.ERROR;
        }
        if (error) { messageBox(ALERTHEADER_DELETION, ERROR_UNABLE_TO_PERFORM, ERROR); }
        return ok;
    }

    private boolean confirmFileDeletion (String strFilePath) {
        return ANSWER_OK == messageBoxConfirmation(ALERTHEADER_DELETION, String.format(PROMPT_FORMAT_FILE_DELETION_CONFIRMATION, strFilePath), CONFIRMATION);
    }

    private String downloadFileByTfi (TableFileInfo tfi) {
        String strErr = ERROR_UNABLE_TO_PERFORM;
        lockOnWatching.lock();
        {
            String strTarget = Paths.get(strCurrentLocalPath, tfi.getFileName()).toString();

            if (isItSafeToDownloadFile(strTarget)) {
                NasMsg nm;
                nm = netClient.transferFile(strCurrentLocalPath, strCurrentServerPath, tfi.toFileInfo(), LOAD2LOCAL);
                if (nm != null) {
                    if (nm.opCode() == OperationCodes.ERROR) {
                        if (nm.msg() != null) { strErr = nm.msg(); }
                    }
                    else if (nm.opCode() == OK) {
                        applyStringAsNewLocalPath(strCurrentLocalPath);
                        strErr = null;
                    }
                }
            }
        }
        lockOnWatching.unlock();
        return strErr;
    }

    private boolean isItSafeToDownloadFile (String strTarget) {
        return !isStringOfRealPath(strTarget) || ANSWER_OK == messageBoxConfirmation(CFactory.ALERTHEADER_DOWNLOADING, String.format(CFactory.PROMPT_FORMAT_REPLACE_CONFIRMATION, strTarget), Alert.AlertType.CONFIRMATION);
    }

    private String uploadFileByTfi (TableFileInfo tfi) {
        String strTargetName = tfi.getFileName();
        String strErr = ERROR_UNABLE_TO_PERFORM + " %";
        FileInfo fi = netClient.fileInfo(strCurrentServerPath, strTargetName);

        if (fi != null) {
            if (fi.isDirectory()) {
                messageBox(ALERTHEADER_LOCAL2SERVER, PROMPT_FOLDERS_EXCHANGE_NOT_SUPPORTED, WARNING);
            }
            else if (isItSafeToUploadFile(strTargetName, fi.isExists())) {
                NasMsg nm = netClient.transferFile(strCurrentLocalPath, strCurrentServerPath, tfi.toFileInfo(), LOAD2SERVER);
                if (nm != null) {
                    if (workUpAListRequestResult(netClient.list(strCurrentServerPath))) {
                        strErr = null;
                    }
                    else if (nm.opCode() == OperationCodes.ERROR) {
                        if (nm.msg() != null) { strErr = nm.msg(); }
                    }
                }
            }
        }
        return strErr;
    }

    private boolean isItSafeToUploadFile (String strTargetName, boolean exists) {
        String strPath = Paths.get(strCurrentServerPath, strTargetName).toString();

        return !exists || ANSWER_OK == messageBoxConfirmation(ALERTHEADER_UPLOADING, sformat(PROMPT_FORMAT_REPLACE_CONFIRMATION, strPath), CONFIRMATION);
    }

    private boolean tryLock (long time, TimeUnit timeUnits) {
        boolean ok = false;
        try {
            ok = lockOnWatching.tryLock(time, timeUnits);
        }
        catch (InterruptedException e) {e.printStackTrace();}
        return ok;
    }
}
