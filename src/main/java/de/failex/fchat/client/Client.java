package de.failex.fchat.client;

import de.failex.fchat.gui.MainGUI;
import de.failex.fchat.gui.MainGUIController;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.net.*;

public class Client {

    Thread incoming;
    Socket server = new Socket();
    String nickname = "User";
    ObjectOutputStream out;
    MainGUIController c;

    //TODO Check ob Verbindung getrennt ist
    public Client(String hostaddress, int port, MainGUIController c) {
        this.c = c;
        try {
            //Check if host is up / hostaddress exists
            InetAddress.getByName(hostaddress);

            InetSocketAddress address = new InetSocketAddress(hostaddress, port);

            server.connect(address);
            MainGUI.connected = true;

            //Disable hostname textfield, port textfield and connect button because we're connected
            c.btn_connect.setDisable(true);
            c.tb_host.setDisable(true);
            c.tb_port.setDisable(true);
            c.tb_nickname.setDisable(true);

            this.nickname = c.tb_nickname.getText();

            //Register as client at the server
            sendMessage(1, "REG");

            incoming = new Thread(new ClientThread());
            incoming.start();

        } catch (UnknownHostException e) {
            Platform.runLater(() ->
                    MainGUI.alert("Connection failed", "Connection failed!", "Unable to connect to the server!\n", Alert.AlertType.ERROR));
        } catch (ConnectException e) {
            Platform.runLater(() ->
                    MainGUI.alert("Server not online", "Server not online!", "There is no server online under that address!\n", Alert.AlertType.ERROR));
        } catch (SocketException e) {
            Platform.runLater(() ->
                    MainGUI.alert("Invalid address", "Invalid address", "You entered an invalid address!\n", Alert.AlertType.ERROR));
        } catch (IOException e) {
            printException(e);
        }
    }

    /**
     * Sends a message or a command to the server
     *
     * @param type  message or command. 0 for message, not 0 for command
     * @param msg   the message itself / the command
     * @param extra some extra content to send
     */
    public void sendMessage(int type, String msg, String... extra) {
        String[] temp = ArrayUtils.addAll(new String[]{type == 0 ? "MSG" : "CMD", nickname, msg}, extra);
        try {
            out = new ObjectOutputStream(server.getOutputStream());
            out.flush();
            out.writeObject(temp);
            out.flush();

        } catch (IOException e) {
            printException(e);
        }
    }

    /**
     * Display an alert with the exception
     *
     * @param e the exception
     */
    private void printException(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String trace = sw.toString();
        Platform.runLater(() ->
                MainGUI.alert("Error", "Error", "An error occured, please report the following\n" + trace, Alert.AlertType.ERROR));
    }

    /**
     * main incoming packet thread
     */
    class ClientThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ObjectInputStream in = new ObjectInputStream(server.getInputStream());
                    Object inobj = in.readObject();

                    //disassmble the string array then check if it's a chat message or a command
                    if (inobj instanceof String[]) {
                        String[] msg = (String[]) inobj;
                        if (msg.length == 0) throw new Exception("empty datapackage!");
                        if (msg.length < 3) throw new Exception("received a corrupted message");
                        switch (msg[0]) {
                            case "MSG":
                                if (!msg[1].isEmpty()) {
                                    String addtolv = "[" + msg[1] + "] " + msg[2];
                                    ObservableList<String> oldlist = c.lv_msg.getItems();
                                    Platform.runLater(() ->
                                            oldlist.add(addtolv));
                                    c.lv_msg.setItems(oldlist);
                                } else {
                                    String addtolv = "*** " + msg[2] + " ***";
                                    ObservableList<String> oldlist = c.lv_msg.getItems();
                                    Platform.runLater(() ->
                                            oldlist.add(addtolv));
                                    c.lv_msg.setItems(oldlist);
                                }
                                break;
                            case "CMD":
                                if (msg[2].equals("NICK") && MainGUI.connected) {
                                    MainGUI.connected = false;
                                    c.tb_nickname.setDisable(false);
                                    c.tb_host.setDisable(false);
                                    c.tb_port.setDisable(false);
                                    c.btn_connect.setDisable(false);
                                    server.close();
                                    MainGUI.disconnect();
                                    //Prevent Not an FX Application Thread error
                                    Platform.runLater(() -> {
                                        MainGUI.alert("Duplicate nickname", "Duplicate nickname", "Sorry but this nickname is already taken!", Alert.AlertType.ERROR);
                                    });
                                    MainGUI.clearChat();
                                    Thread.currentThread().interrupt();
                                    return;
                                } else if (msg[2].equals("FULL") && MainGUI.connected) {
                                    MainGUI.connected = false;
                                    c.tb_nickname.setDisable(false);
                                    c.tb_host.setDisable(false);
                                    c.tb_port.setDisable(false);
                                    c.btn_connect.setDisable(false);
                                    
                                    //Prevent Not an FX Application Thread error
                                    Platform.runLater(() -> {
                                        MainGUI.alert("Room full", "Romm full!", "Sorry but the chatroom is full", Alert.AlertType.ERROR);
                                    });
                                    MainGUI.clearChat();
                                    server.close();
                                    MainGUI.disconnect();
                                    Thread.currentThread().interrupt();
                                    return;
                                } else if (msg[2].equals("KICK") && MainGUI.connected || msg[2].equals("BAN") && MainGUI.connected) {
                                    MainGUI.connected = false;
                                    c.tb_nickname.setDisable(false);
                                    c.tb_host.setDisable(false);
                                    c.tb_port.setDisable(false);
                                    c.btn_connect.setDisable(false);

                                    //Disable mod tools
                                    c.btn_broadcast.setDisable(true);
                                    c.btn_clear.setDisable(true);
                                    c.btn_ban.setDisable(true);
                                    c.btn_kick.setDisable(true);

                                    if (msg[2].equals("KICK")) {
                                        //Prevent Not an FX Application Thread error
                                        Platform.runLater(() -> MainGUI.alert("Kicked", "Kicked by server", "You got kicked by the server", Alert.AlertType.INFORMATION));
                                    } else {
                                        Platform.runLater(() -> MainGUI.alert("Banned", "Banned", "You are banned from the server!", Alert.AlertType.ERROR));
                                    }
                                    MainGUI.clearChat();
                                    server.close();
                                    MainGUI.disconnect();
                                    Thread.currentThread().interrupt();
                                    return;
                                } else if (msg[2].equals("MODC") && MainGUI.connected) {
                                    String password = msg[3];
                                    Platform.runLater(() ->
                                            MainGUI.alert("Added as mod", "Added as mod", "You have been added as a moderator! In the future\n" +
                                                    "you'll need the password '" + password + "' to connect to the server", Alert.AlertType.CONFIRMATION));
                                } else if (msg[2].equals("NOTREG")) {
                                    MainGUI.connected = false;
                                    c.tb_nickname.setDisable(false);
                                    c.tb_host.setDisable(false);
                                    c.tb_port.setDisable(false);
                                    c.btn_connect.setDisable(false);
                                    Platform.runLater(() -> MainGUI.alert("Login failed", "Logged failed", "The Mod-login has failed due to a missing registration!", Alert.AlertType.ERROR));
                                    MainGUI.clearChat();
                                    server.close();
                                    MainGUI.disconnect();
                                    Thread.currentThread().interrupt();
                                    return;
                                } else if (msg[2].equals("NOPASSWD")) {
                                    Platform.runLater(() -> MainGUI.alert("Missing password", "Missing password", "You failed to enter a password", Alert.AlertType.ERROR));
                                } else if (msg[2].equals("REGSUCCESS")) {
                                    Platform.runLater(() -> MainGUI.alert("Login successful", "Success!", "You have successfully logged in as a mod", Alert.AlertType.INFORMATION));
                                    c.btn_broadcast.setDisable(false);
                                    c.btn_clear.setDisable(false);
                                    c.btn_ban.setDisable(false);
                                    c.btn_kick.setDisable(false);
                                } else if (msg[2].equals("WRONGPASSWD")) {
                                    Platform.runLater(() -> MainGUI.alert("Wrong password", "Incorrect password", "The password you have entered is wrong!", Alert.AlertType.ERROR));
                                } else if (msg[2].equals("ALREADYLOGGED")) {
                                    Platform.runLater(() -> MainGUI.alert("Already logged in", "Already a mod", "You are already logged in as a mod!", Alert.AlertType.INFORMATION));
                                } else if (msg[2].equals("NOMOD")) {
                                    Platform.runLater(() -> MainGUI.alert("No mod", "No mod", "You're no mod so you can't login as one!", Alert.AlertType.ERROR));
                                } else if (msg[2].equals("MODL")) {
                                    final String[] passwd = {""};
                                    Platform.runLater(() -> {
                                        Alert a = new Alert(Alert.AlertType.INFORMATION);
                                        a.setTitle("Login");
                                        a.setHeaderText("Please enter your password!");
                                        TextField tb_passwd = new TextField();
                                        a.getDialogPane().setContent(tb_passwd);
                                        a.setOnCloseRequest((e) -> passwd[0] = tb_passwd.getText());
                                        a.showAndWait();
                                        sendMessage(1, "REGMOD", passwd);
                                    });
                                } else if (msg[2].equals("NOTCONNECTED")) {
                                    MainGUI.connected = false;
                                    c.tb_nickname.setDisable(false);
                                    c.tb_host.setDisable(false);
                                    c.tb_port.setDisable(false);
                                    c.btn_connect.setDisable(false);
                                    Platform.runLater(() -> MainGUI.alert("Not registered!", "Not registered!", "You tried to send a message without being registered at the server!", Alert.AlertType.ERROR));
                                    MainGUI.clearChat();

                                    //Disable mod tools
                                    c.btn_broadcast.setDisable(true);
                                    c.btn_clear.setDisable(true);
                                    c.btn_ban.setDisable(true);
                                    c.btn_kick.setDisable(true);

                                    server.close();
                                    MainGUI.disconnect();
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                break;
                        }
                    }
                } catch (SocketException ef) {
                    MainGUI.connected = false;
                    c.tb_nickname.setDisable(false);
                    c.tb_host.setDisable(false);
                    c.tb_port.setDisable(false);
                    c.btn_connect.setDisable(false);

                    //Disable mod tools
                    c.btn_broadcast.setDisable(true);
                    c.btn_clear.setDisable(true);
                    c.btn_ban.setDisable(true);
                    c.btn_kick.setDisable(true);

                    Platform.runLater(() -> MainGUI.alert("Connection lost", "Connection lost!", "Connection to the server lost! Logging out...", Alert.AlertType.INFORMATION));
                    MainGUI.disconnect();
                    MainGUI.clearChat();
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    printException(e);
                }
            }
        }
    }

}
