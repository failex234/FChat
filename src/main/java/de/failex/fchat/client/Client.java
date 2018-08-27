package de.failex.fchat.client;

import de.failex.fchat.gui.MainGUI;
import de.failex.fchat.gui.MainGUIController;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


public class Client {

    Thread incoming;
    Socket server = new Socket();
    String nickname = "User";
    ObjectOutputStream out;
    MainGUIController c;

    UUID clientid;

    //TODO Check ob Verbindung getrennt ist
    public Client(String hostaddress, int port, MainGUIController c, int version, UUID clientid) {
        this.c = c;
        this.clientid = clientid;
        try {
            //Check if host is up / hostaddress exists
            InetAddress.getByName(hostaddress);

            InetSocketAddress address = new InetSocketAddress(hostaddress, port);

            server.connect(address);
            MainGUI.connected = true;

            //Disable hostname textfield, port textfield and connect button
            c.btn_connect.setDisable(true);
            c.tb_host.setDisable(true);
            c.tb_port.setDisable(true);
            c.tb_nickname.setDisable(true);

            c.btn_msg.setDisable(false);

            this.nickname = c.tb_nickname.getText();

            //Register as client at the server and add the protocol version to ensure that both are using the same protocol
            sendMessage(1, version + "REG");

            incoming = new Thread(new ClientThread());
            incoming.start();

        } catch (UnknownHostException e) {
            Platform.runLater(() ->
                    MainGUI.alert("Connection failed", "Connection failed!", "Unable to connect to the server!\n", Alert.AlertType.ERROR));

            //Enable everything again since the connection was not successful
            c.btn_connect.setDisable(false);
            c.tb_host.setDisable(false);
            c.tb_port.setDisable(false);
            c.tb_nickname.setDisable(false);
            c.btn_msg.setDisable(true);
            MainGUI.connected = false;
            incoming.interrupt();
        } catch (ConnectException e) {
            Platform.runLater(() ->
                    MainGUI.alert("Server not online", "Server not online!", "There is no server online under that address!\n", Alert.AlertType.ERROR));

            //Enable everything again since the connection was not successful
            c.btn_connect.setDisable(false);
            c.tb_host.setDisable(false);
            c.tb_port.setDisable(false);
            c.tb_nickname.setDisable(false);
            c.btn_msg.setDisable(true);
            MainGUI.connected = false;
            incoming.interrupt();
        } catch (SocketException e) {
            Platform.runLater(() ->
                    MainGUI.alert("Invalid address", "Invalid address", "You entered an invalid address!\n", Alert.AlertType.ERROR));

            //Enable everything again since the connection was not successful
            c.btn_connect.setDisable(false);
            c.tb_host.setDisable(false);
            c.tb_port.setDisable(false);
            c.tb_nickname.setDisable(false);
            c.btn_msg.setDisable(true);
            MainGUI.connected = false;
            incoming.interrupt();
        } catch (IOException e) {
            printException(e);

            //Enable everything again since the connection was not successful
            c.btn_connect.setDisable(false);
            c.tb_host.setDisable(false);
            c.tb_port.setDisable(false);
            c.tb_nickname.setDisable(false);
            c.btn_msg.setDisable(true);
            MainGUI.connected = false;
            incoming.interrupt();
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
        String[] temp = ArrayUtils.addAll(new String[]{type == 0 ? "MSG" : "CMD", clientid.toString(), nickname, msg}, extra);
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
                                    c.btn_msg.setDisable(true);
                                    server.close();
                                    MainGUI.disconnect();
                                    //Prevent Not an FX Application Thread error
                                    Platform.runLater(() -> {
                                        MainGUI.alert("Duplicate nickname", "Duplicate nickname", "Sorry but this nickname is already taken!", Alert.AlertType.ERROR);
                                    });
                                    MainGUI.clearChat();
                                    MainGUI.clearClientList();
                                    Thread.currentThread().interrupt();
                                    return;
                                } else if (msg[2].equals("FULL") && MainGUI.connected) {
                                    MainGUI.connected = false;
                                    c.tb_nickname.setDisable(false);
                                    c.tb_host.setDisable(false);
                                    c.tb_port.setDisable(false);
                                    c.btn_connect.setDisable(false);
                                    c.btn_msg.setDisable(true);

                                    //Prevent Not an FX Application Thread error
                                    Platform.runLater(() -> {
                                        MainGUI.alert("Room full", "Romm full!", "Sorry but the chatroom is full", Alert.AlertType.ERROR);
                                    });
                                    MainGUI.clearChat();
                                    MainGUI.clearClientList();
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
                                    c.btn_msg.setDisable(true);

                                    if (msg[2].equals("KICK")) {
                                        //Prevent Not an FX Application Thread error
                                        Platform.runLater(() -> MainGUI.alert("Kicked", "Kicked by server", "You got kicked by the server", Alert.AlertType.INFORMATION));
                                    } else {
                                        Platform.runLater(() -> MainGUI.alert("Banned", "Banned", "You are banned from the server!", Alert.AlertType.ERROR));
                                    }
                                    MainGUI.clearChat();
                                    MainGUI.clearClientList();
                                    server.close();
                                    MainGUI.disconnect();
                                    Thread.currentThread().interrupt();
                                    return;
                                } else if (msg[2].equals("MODC") && MainGUI.connected) {
                                    Platform.runLater(() ->
                                            MainGUI.alert("Added as mod", "Added as mod", "Congratulations you're now a moderator!\nYou will keep the status throughout reconnections", Alert.AlertType.CONFIRMATION));

                                    //Enable mod tools
                                    c.btn_broadcast.setDisable(false);
                                    c.btn_clear.setDisable(false);
                                    c.btn_ban.setDisable(false);
                                    c.btn_kick.setDisable(false);
                                    c.btn_msg.setDisable(false);
                                } else if (msg[2].equals("REGSUCCESS")) {
                                    Platform.runLater(() -> MainGUI.alert("Login successful", "Success!", "You have successfully logged in as a mod", Alert.AlertType.INFORMATION));
                                    c.btn_broadcast.setDisable(false);
                                    c.btn_clear.setDisable(false);
                                    c.btn_ban.setDisable(false);
                                    c.btn_kick.setDisable(false);
                                    c.btn_msg.setDisable(false);
                                } else if (msg[2].equals("NOTCONNECTED")) {
                                    MainGUI.connected = false;
                                    c.tb_nickname.setDisable(false);
                                    c.tb_host.setDisable(false);
                                    c.tb_port.setDisable(false);
                                    c.btn_connect.setDisable(false);
                                    Platform.runLater(() -> MainGUI.alert("Not registered!", "Not registered!", "You tried to send a message without being registered at the server!", Alert.AlertType.ERROR));
                                    MainGUI.clearChat();
                                    MainGUI.clearClientList();

                                    //Disable mod tools
                                    c.btn_broadcast.setDisable(true);
                                    c.btn_clear.setDisable(true);
                                    c.btn_ban.setDisable(true);
                                    c.btn_kick.setDisable(true);
                                    c.btn_msg.setDisable(true);
                                    c.btn_msg.setDisable(true);

                                    server.close();
                                    MainGUI.disconnect();
                                    Thread.currentThread().interrupt();
                                    return;
                                } else if (msg[2].equals("LIST")) {
                                    //TODO: Remove first three elements
                                    String[] clientsarray = new String[msg.length - 3];
                                    int clientarrayindex = 0;
                                    for (int i = 3; i < msg.length; i++) {
                                        clientsarray[clientarrayindex] = msg[i];
                                        clientarrayindex++;
                                    }
                                    List<String> clients = Arrays.asList(clientsarray);
                                    c.lv_clients.setItems(FXCollections.observableArrayList(clients));
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
                    c.btn_msg.setDisable(true);

                    Platform.runLater(() -> MainGUI.alert("Connection lost", "Connection lost!", "Connection to the server lost! Logging out...", Alert.AlertType.INFORMATION));
                    MainGUI.disconnect();
                    MainGUI.clearChat();
                    MainGUI.clearClientList();
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    if (e instanceof EOFException) {
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
                        c.btn_msg.setDisable(true);

                        Platform.runLater(() -> MainGUI.alert("Connection lost", "Connection lost!", "Connection to the server lost! Logging out...", Alert.AlertType.INFORMATION));
                        MainGUI.disconnect();
                        MainGUI.clearChat();
                        MainGUI.clearClientList();
                        Thread.currentThread().interrupt();
                        return;
                    } else {
                        printException(e);
                    }
                }
            }
        }
    }

}
