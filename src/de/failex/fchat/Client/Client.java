package de.failex.fchat.Client;

import de.failex.fchat.GUI.MainGUI;
import de.failex.fchat.GUI.MainGUIController;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {

    Thread incoming;
    Socket server;
    String nickname = "User";
    MainGUIController c;

    //TODO Client registrieren mit CMD!
    public Client(String hostaddress, int port, MainGUIController c) {
        this.c = c;
        try {
            InetAddress.getByName(hostaddress);

            InetSocketAddress address = new InetSocketAddress(hostaddress, port);

            server = new Socket();
            server.connect(address);

            ObservableList<String> old = c.lv_msg.getItems();
            old.add("Erfolgreich verbunden!");
            c.lv_msg.setItems(old);
            MainGUI.connected = true;

            //Nicknames cant contain Spaces!
            sendMessage(1, "REG");

            incoming = new Thread(() -> {
                try {
                    ObjectInputStream in = new ObjectInputStream(server.getInputStream());
                    Object inobj = in.readObject();

                    if (inobj instanceof String[]) {
                        String[] msg = (String[]) inobj;
                        if (msg.length == 0) throw new Exception("Leerer Nachrichteninhalt!");
                        if (msg.length < 3) throw new Exception("Unfertige Nachricht angekommen");
                        switch (msg[0]) {
                            case "MSG":
                                //TODO: Remove
                                String addtolv = "[" + msg[1] + "] " + msg[2];
                                ObservableList<String> oldlist = c.lv_msg.getItems();
                                oldlist.add(addtolv);
                                c.lv_msg.setItems(oldlist);
                                break;
                            case "CMD":
                                //TODO: Befehle schreiben
                                break;
                        }
                    }
                } catch (Exception e) {
                    printException(e);
                }
            });

            incoming.start();

        } catch (UnknownHostException e) {
            MainGUI.alert("Verbindung fehlgeschlagen", "Verbindung fehlgeschlagen!", "Es konnte keine Verbindung hergestellt werden!\n", Alert.AlertType.ERROR);
        } catch (IOException e) {
            printException(e);
        }
    }

    public void sendMessage(int type, String msg) {
        if (nickname == null || nickname.isEmpty()) {
            nickname = "";
        }
        String[] temp = {type == 0 ? "MSG" : "CMD", nickname, msg};


        String addtolv = "[" + nickname + "] " + msg;
        ObservableList<String> oldlist = c.lv_msg.getItems();
        oldlist.add(addtolv);
        c.lv_msg.setItems(oldlist);

        try {
            ObjectOutputStream out = new ObjectOutputStream(server.getOutputStream());

            out.writeObject(temp);
            out.flush();
            out.close();
            //TODO: Vielleicht Socket flushen!
        } catch (IOException e) {
            printException(e);
        }
    }

    private void printException(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String trace = sw.toString();
        MainGUI.alert("Fehler", "Fehler", "Es hab einen Fehler, bitte das hier melden:\n" + trace, Alert.AlertType.ERROR);
    }
}
