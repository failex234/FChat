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
    Socket server = new Socket();
    String nickname = "User";
    ObjectOutputStream out;
    MainGUIController c;

    //TODO Check ob Verbindung getrennt ist
    public Client(String hostaddress, int port, MainGUIController c) {
        this.c = c;
        try {
            InetAddress.getByName(hostaddress);

            InetSocketAddress address = new InetSocketAddress(hostaddress, port);

            server.connect(address);

            ObservableList<String> old = c.lv_msg.getItems();
            old.add("Erfolgreich verbunden!");
            c.lv_msg.setItems(old);
            MainGUI.connected = true;

            c.btn_connect.setDisable(true);
            c.tb_host.setDisable(true);
            c.tb_port.setDisable(true);

            //Nicknames cant contain Spaces!
            sendMessage(1, "REG");

            incoming = new Thread(new ClientThread());
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


        try {

            out = new ObjectOutputStream(server.getOutputStream());
            out.flush();
            out.writeObject(temp);
            out.flush();

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


    class ClientThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ObjectInputStream in = new ObjectInputStream(server.getInputStream());
                    Object inobj = in.readObject();

                    if (inobj instanceof String[]) {
                        String[] msg = (String[]) inobj;
                        if (msg.length == 0) throw new Exception("Leerer Nachrichteninhalt!");
                        if (msg.length < 3) throw new Exception("Unfertige Nachricht angekommen");
                        switch (msg[0]) {
                            case "MSG":
                                if (!msg[1].isEmpty()) {
                                    String addtolv = "[" + msg[1] + "] " + msg[2];
                                    ObservableList<String> oldlist = c.lv_msg.getItems();
                                    oldlist.add(addtolv);
                                    c.lv_msg.setItems(oldlist);
                                } else {
                                    String addtolv = "*** " + msg[2] + " ***";
                                    ObservableList<String> oldlist = c.lv_msg.getItems();
                                    oldlist.add(addtolv);
                                    c.lv_msg.setItems(oldlist);
                                }
                                break;
                            case "CMD":
                                //TODO: Befehle schreiben
                                break;
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof IllegalStateException) {
                        //Not on FX application thread
                    } else {
                        printException(e);
                    }
                }
            }
        }
    }

}
