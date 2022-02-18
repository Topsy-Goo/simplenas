package ru.gb.simplenas.client;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

import static ru.gb.simplenas.client.CFactory.*;
import static ru.gb.simplenas.client.Controller.messageBox;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;
import static ru.gb.simplenas.common.services.impl.NasFileManager.isNameValid;

public class DlgLoginController implements Initializable {

    @FXML public TextField txtfldLogin;
    @FXML public TextField txtfldPassword;
    @FXML public PasswordField pswfldPassword;
    @FXML public Button buttnLogin;
    @FXML public Button buttnCancel;
    @FXML public CheckBox checkBox;
    @FXML public VBox dlgloginroot;

    private Stage dialogStage;
    private String login;
    private String password;
    private boolean buttnLoginPressed;


    @Override public void initialize (URL location, ResourceBundle resources) {

        //Наделяем поле ввода пароля возможностью показывать введённые символы. Для этой цели созданы
        //  два поля ввода — обычное (TextField) и «парольное» (PasswordField) и создан флажок (чекбокс).
        //  Одно из полей будет невидимо в зависимости от состояния флажка. Устанавливаем связи между
        //  этими полями и флажком:

        //если флажок установлен, то будет отображаться обычное поле ввода:
        txtfldPassword.managedProperty().bind(checkBox.selectedProperty());
        txtfldPassword.visibleProperty().bind(checkBox.selectedProperty());

        //если чекбокс сброшен, то будет отображаться «парольное» поле ввода:
        pswfldPassword.managedProperty().bind(checkBox.selectedProperty().not());
        pswfldPassword.visibleProperty().bind(checkBox.selectedProperty().not());

        //делаем текст в обоих полях ввода одинаковым.
        txtfldPassword.textProperty().bindBidirectional(pswfldPassword.textProperty());
    }
//---------------------------------------------------------------------------------------------------------------*/

/** Если при вводе логина пользователь нажал ENTER, то переводим фокус ввода на следующий элемент управления —
  на поле ввода пароля. */
    @FXML public void onActionLoginTypingDone (ActionEvent actionEvent) {
        setFocusOnPasswordControl();
    }

/** Если при вводе пароля пользователь нажал ENTER, то переводим фокус ффода на следующий элемент управления —
  на кнопку продолжения авторизации или на кнопку отмены, в зависимости от содержимого полей ввода логина
  и пароля.   */
    @FXML public void onActionPasswordTypingDone (ActionEvent actionEvent) {
        String login = txtfldLogin.getText();
        String pass = getTextFromPasswordControl();

        if (sayNoToEmptyStrings(login, pass)) { buttnLogin.requestFocus(); }
        else { buttnCancel.requestFocus(); }
    }

//Нажатие на кнопку «Авторизация»   */
    @FXML public void onActionStartLogin (ActionEvent actionEvent) {
        login = txtfldLogin.getText();
        password = getTextFromPasswordControl();

        if (!isNameValid (login)) {
            messageBox(ALERTHEADER_AUTHENTIFICATION, PROMPT_INVALID_USER_NAME, Alert.AlertType.ERROR);
            txtfldLogin.requestFocus();
        }
        else if (!sayNoToEmptyStrings(password)) {
            messageBox(ALERTHEADER_AUTHENTIFICATION, PROMPT_INVALID_PASSWORD, Alert.AlertType.ERROR);
            setFocusOnPasswordControl();
        }
        else {
            buttnLoginPressed = true;
            dialogStage.close();
        }
    }

/** Нажатие на кнопку «Отмена».   */
    @FXML public void onActionCancel (ActionEvent actionEvent) {
        login = null;
        password = null;
        buttnLoginPressed = false;
        dialogStage.close();
    }

    public boolean isButtnLoginPressed () { return buttnLoginPressed; }

    public String getLogin () { return login; }

    public String getPassword () { return password; }

    public void setDialogStage (Stage dialogStage) { this.dialogStage = dialogStage; }

    private void setFocusOnPasswordControl () {
        if (txtfldPassword.isVisible()) { txtfldPassword.requestFocus(); }
        else { pswfldPassword.requestFocus(); }
    }

    private String getTextFromPasswordControl () {
        return txtfldPassword.isVisible() ? txtfldPassword.getText() : pswfldPassword.getText();
    }
}
