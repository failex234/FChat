package de.failex.fchat.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.failex.fchat.CommonUtils;
import de.failex.fchat.Cryptography;
import de.failex.fchat.client.Client;
import de.failex.fchat.client.ClientConfig;
import de.failex.fchat.server.ServerConfig;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.*;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

public class MainGUI {

    public static boolean connected = false;
    private ClientConfig clientcfg;
    private UUID clientid;
    private static Client cl;
    private static MainGUIController c;

    MainGUI(MainGUIController c, Stage stage, boolean forcelaunch) {
        File datafolder = new File("data");
        File lockfile = new File(("data" + File.separator + "fchat.lock").replace(" ", ""));
        if (lockfile.exists() && !forcelaunch) {
            System.err.println("[ERR] An instance of FChat is already running! Refusing to launch.");
            System.err.println("If you're sure that no server or client is running you can try");
            System.err.println("rerunning the client with the --force flag");
            System.err.println("ex. FChat --force");
            System.exit(1);
        } else if (!lockfile.exists()) {
            datafolder.mkdir();
            int pid = CommonUtils.getPID();
            try {
                lockfile.createNewFile();
                PrintWriter pw = new PrintWriter(lockfile);
                pw.println(pid);
                pw.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                System.err.println("Can't create lockfile. Please make sure the permissions are set correctly!");
            }
        }

        //TODO signal handler to save config / to save nickname to config
        MainGUI.c = c;
        //Set standard port
        c.tb_port.setText("1552");
        c.tb_host.setText("");
        c.btn_connect.setDisable(true);

        c.btn_send.setDisable(true);

        c.btn_kick.setDisable(true);
        c.btn_ban.setDisable(true);
        c.btn_clear.setDisable(true);
        c.btn_broadcast.setDisable(true);
        c.btn_msg.setDisable(true);

        c.title.setVisible(false);

        File config = new File(("data " + File.separator + "clientcfg.json").replace(" ", ""));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if(datafolder.exists() && datafolder.isDirectory() && config.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(config))) {
                StringBuilder sb = new StringBuilder();
                String line = reader.readLine();

                while (line != null) {
                    sb.append(line).append("\n");
                    line = reader.readLine();
                }
                clientcfg = gson.fromJson(sb.toString(), ClientConfig.class);
                clientid = clientcfg.getClientid();
                c.tb_nickname.setText(clientcfg.getNickname());

            } catch (IOException e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                String trace = sw.toString();

                alert("Error", "Error while reading config", "Can't load config because of\n" + trace, Alert.AlertType.ERROR);
            }
        } else {
            datafolder.mkdir();
            clientcfg = new ClientConfig();
            clientid = UUID.randomUUID();
            clientcfg.setClientid(clientid);

            String newcfg = gson.toJson(clientcfg);

            config.delete();

            try {
                PrintWriter pw = new PrintWriter(config);
                pw.write(newcfg);
                pw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            alert("Welcome to FChat!", "First start","Welcome to FChat, the work in progress Chat written in Java!", Alert.AlertType.INFORMATION);
        }

            try {
                if (!Cryptography.keysExist()) {
                    KeyPair temp = Cryptography.generateKeyPair();
                    Cryptography.saveKeys(temp.getPublic(), temp.getPrivate());
                }

            } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
                e.printStackTrace();
            }


        //TODO remove horizontal scrolling !important

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
                clientcfg.setNickname(c.tb_nickname.getText());
                cl = new Client(c.tb_host.getText(), Integer.parseInt(c.tb_port.getText()), c, clientid);
            }
        });

        //Send the message to the server
        c.btn_send.setOnMouseClicked(event -> {
            if (connected) {
                if (getCount(c.tb_msg.getText(), '\n') > 2) {
                    alert("Too many line breaks", "Too many line breaks", "You're message contains too many line breaks!", Alert.AlertType.WARNING);
                    return;
                }

                //Type 0 = MSG, Type 1 = CMD
                cl.sendMessage(0, c.tb_msg.getText());
                c.tb_msg.setText("");
                c.btn_send.setDisable(true);
            } else {
                alert("Not connected", "Not connected", "You're not connected to a server!", Alert.AlertType.INFORMATION);
            }
        });

        c.btn_msg.setOnMouseClicked(event -> {
            String name = (String) c.lv_clients.getSelectionModel().getSelectedItem();
            if (name != null && !name.isEmpty()) {
                cl.sendMessage(0, "/msg " + name + " " + c.tb_msg.getText());
                c.tb_msg.setText("");
            }

        });

        c.btn_ban.setOnMouseClicked(event -> {
            String name = (String) c.lv_clients.getSelectionModel().getSelectedItem();
            if (name != null && !name.isEmpty()) {
                cl.sendMessage(0, "/ban " + name);
            }
        });

        c.btn_kick.setOnMouseClicked(event -> {
            String name = (String) c.lv_clients.getSelectionModel().getSelectedItem();
            if (name != null && !name.isEmpty()) {
                cl.sendMessage(0, "/kick " + name);
            }
        });

        c.btn_clear.setOnMouseClicked(event -> cl.sendMessage(0, "/clear"));

        c.btn_broadcast.setOnMouseClicked(event -> {
            cl.sendMessage(0, "/broadcast " + c.tb_msg.getText());
            c.tb_msg.setText("");
        });
    }

    /**
     * Displays an alert
     *
     * @param title   alert title
     * @param header  alert header
     * @param content alert content
     * @param type    alert type
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

    public static void disconnect() {
        cl = null;
    }

    /**
     * Counts the occurrences of a character in a string
     *
     * @param string the string to search through
     * @param search the character to count
     * @return the number of occurrences of the character in the string
     */
    private int getCount(String string, char search) {
        int count = 0;
        for (char c : string.toCharArray()) {
            if (c == search) count++;
        }

        return count;
    }

    public static void clearChat() {
        ObservableList oldlist = c.lv_msg.getItems();
        Platform.runLater(oldlist::clear);
        c.lv_msg.setItems(oldlist);
    }

    public static void clearClientList() {
        ObservableList oldlist = c.lv_clients.getItems();
        Platform.runLater(oldlist::clear);
        c.lv_clients.setItems(oldlist);
    }
}
