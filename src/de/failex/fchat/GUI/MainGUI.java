package de.failex.fchat.GUI;

import de.failex.fchat.Client.Client;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

public class MainGUI {

    //TODO Let user choose username
    public static boolean connected = false;
    Client cl;

    public MainGUI(MainGUIController c, Stage stage) {
        c.tb_port.setText("1552");
        c.tb_host.setText("127.0.0.1");

        c.btn_send.setDisable(true);

        //TODO: Wrapping benötigt. sonst horizontaler Scrollbalken
        c.lv_msg.setMaxWidth(560);

        c.tb_host.setOnKeyTyped(event -> {
            if (!c.tb_host.getText().isEmpty() && !c.tb_port.getText().isEmpty()) {
                c.btn_connect.setDisable(false);
            } else {
                c.btn_connect.setDisable(true);
            }
        });

        c.tb_port.setOnKeyTyped(event -> {
            if (!c.tb_host.getText().isEmpty() && !c.tb_port.getText().isEmpty()) {
                c.btn_connect.setDisable(false);
            } else {
                c.btn_connect.setDisable(true);
            }
        });

        c.tb_msg.setOnKeyTyped(event -> {
            if (!c.tb_msg.getText().isEmpty()) {
                c.btn_send.setDisable(false);
            } else {
                c.btn_send.setDisable(true);
            }
        });

        c.btn_connect.setOnMouseClicked(event -> {
            cl = new Client(c.tb_host.getText(), Integer.parseInt(c.tb_port.getText()), c);
        });

        c.btn_send.setOnMouseClicked(event -> {
            if (connected) {
                //Type 0 = MSG, Type 1 = CMD
                cl.sendMessage(0, c.tb_msg.getText());
                c.tb_msg.setText("");
                c.btn_send.setDisable(true);
            } else {
                alert("Nicht veerbunden", "Nicht verbunden", "Du bist derzeit mit keinem Server verbunden!", Alert.AlertType.INFORMATION);
            }
        });
    }

    public static void alert(String title, String header, String content, Alert.AlertType type) {
        Alert a = new Alert(type);
        if (type.equals(Alert.AlertType.ERROR)) {
            TextArea ta = new TextArea(content);
            ta.setWrapText(true);
            ta.setEditable(false);
            a.getDialogPane().setPrefWidth(800);
            a.getDialogPane().setPrefHeight(300);
            a.getDialogPane().setContent(ta);
        } else {
            Label lbl = new Label(content);
            lbl.setWrapText(true);
            a.getDialogPane().setContent(lbl);
        }
        a.setTitle(title);
        a.setHeaderText(header);
        a.showAndWait();
    }
}
