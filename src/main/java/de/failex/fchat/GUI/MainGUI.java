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
        //Set standard port
        c.tb_port.setText("1552");
        c.tb_host.setText("");
        c.btn_connect.setDisable(true);

        c.btn_send.setDisable(true);

        //TODO remove horizontal scrolling

        //Only enable connect button when both the hostname and port field are not empty
        c.tb_host.setOnKeyTyped(event -> {
            if (!c.tb_host.getText().isEmpty() && !c.tb_port.getText().isEmpty() && !c.tb_nickname.getText().isEmpty()) {
                c.btn_connect.setDisable(false);
            } else {
                c.btn_connect.setDisable(true);
            }
        });

        //Only enable connect button when both the hostname and port field are not empty
        c.tb_port.setOnKeyTyped(event -> {
            if (!c.tb_host.getText().isEmpty() && !c.tb_port.getText().isEmpty() && !c.tb_nickname.getText().isEmpty()) {
                c.btn_connect.setDisable(false);
            } else {
                c.btn_connect.setDisable(true);
            }
        });

        //Only enable connect button when both the hostname and port field are not empty
        c.tb_nickname.setOnKeyTyped(event -> {
            if (!c.tb_host.getText().isEmpty() && !c.tb_port.getText().isEmpty() && !c.tb_nickname.getText().isEmpty()) {
                c.btn_connect.setDisable(false);
            } else {
                c.btn_connect.setDisable(true);
            }
        });

        //Enable the send button when the message textfield is not empty
        c.tb_msg.setOnKeyTyped(event -> {
            if (!c.tb_msg.getText().isEmpty()) {
                c.btn_send.setDisable(false);
            } else {
                c.btn_send.setDisable(true);
            }
        });

        //Create a new client
        c.btn_connect.setOnMouseClicked(event -> {
            if (c.tb_nickname.getText().contains(" ")) {
                alert("No spaces", "No spaces", "Sorry but you can't have spaces in your nickname!", Alert.AlertType.INFORMATION);
            } else {
                cl = new Client(c.tb_host.getText(), Integer.parseInt(c.tb_port.getText()), c);
            }
        });

        //Send the message to the server
        c.btn_send.setOnMouseClicked(event -> {
            if (connected) {
                //Type 0 = MSG, Type 1 = CMD
                cl.sendMessage(0, c.tb_msg.getText());
                c.tb_msg.setText("");
                c.btn_send.setDisable(true);
            } else {
                alert("Not connected", "Not connected", "You're not connected to a server yet!", Alert.AlertType.INFORMATION);
            }
        });
    }

    /**
     * Displays an alert
     * @param title alert title
     * @param header alert header
     * @param content alert content
     * @param type alert type
     */
    public static void alert(String title, String header, String content, Alert.AlertType type) {
        Alert a = new Alert(type);
        if (type.equals(Alert.AlertType.ERROR) && title.equals("Error")) {
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
