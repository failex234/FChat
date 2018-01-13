package de.failex.fchat.Server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class Server {

    //TODO: Clients registrieren lassen mit CMD
    ServerSocket mainserver;
    Thread incoming;

    ArrayList<Socket> clients = new ArrayList<>();
    HashMap<Socket, String> clientnames = new HashMap<>();

    public Server(int port) {
        try {
            log("Es wird versucht Port " + port + " zu binden");
            mainserver = new ServerSocket(port);
            log("Port gebinded!");

            incoming = new Thread(() -> {
                try {
                    Socket temp = mainserver.accept();

                    ObjectInputStream in = new ObjectInputStream(temp.getInputStream());
                    Object inobj = in.readObject();

                    log("Einkommendes Paket");

                    if (inobj instanceof String[]) {
                        if (((String[]) inobj).length < 3) {
                            log("Datenpaket hat unerwartete Länge");
                            return;
                        }

                        String[] msg = (String[]) inobj;
                        System.out.print("[LOG] Paketinhalt: ");
                        for (String i : msg)  {
                            System.out.print(i + " ");
                        }
                        System.out.print("\n");

                        switch (msg[0]) {
                            case "MSG":
                                log("Paket von " + temp.getInetAddress().toString() + ": " + msg[0] + " mit " + msg[2]);
                                sendToAllClients(msg);
                                break;
                            case "CMD":
                                if (msg[2].equals("REG")) {
                                    clients.add(temp);
                                    clientnames.put(temp, msg[1]);
                                    log("Client " + temp.getInetAddress().toString() + " registered as " + msg[1]);
                                }
                                break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            });

            incoming.start();

            log("Server erfolgreich gestartet, es wird auf Verbindungen an Port " + port + " gewartet...");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void sendToAllClients(String[] msg) {
        for (Socket client : clients) {
            sendToClient(client, msg);
        }
    }

    private void sendToClient(Socket client, String[] msg) {
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(client.getOutputStream());
            out.writeObject(msg);
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        System.out.println("[LOG] " + msg);
    }
}
