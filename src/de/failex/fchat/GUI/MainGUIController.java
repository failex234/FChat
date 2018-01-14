package de.failex.fchat.GUI;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;

public class MainGUIController {

    @FXML
    public Label title;

    @FXML
    public Pane pane;

    @FXML
    public TextField tb_host;

    @FXML
    public TextField tb_port;

    @FXML
    public Button btn_connect;

    @FXML
    public ListView lv_msg;

    @FXML
    public TextArea tb_msg;

    @FXML
    public Button btn_send;

    @FXML
    public Label lbl_nickname;

    @FXML
    public Label lbl_host;

    @FXML
    public Label lbl_port;

    @FXML
    public TextField tb_nickname;
}
