package de.failex.fchat.Server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;

public class Server {

    //TODO: Clients registrieren lassen mit CMD
    ServerSocket mainserver;
    Thread serverthread = null;
    int port;

    ArrayList<Socket> clients = new ArrayList<>();
    HashMap<Socket, String> clientnames = new HashMap<>();

    public Server(int port) {
        this.port = port;
            log("Server erfolgreich gestartet, es wird auf Verbindungen an Port " + port + " gewartet...");
            this.serverthread = new Thread(new ServerThread());
            this.serverthread.start();
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        System.out.println("[LOG] " + msg);
    }

    class ServerThread implements Runnable {

        @Override
        public void run() {
            Socket socket = null;
            try {
                mainserver = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = mainserver.accept();
                    SocketThread st = new SocketThread(socket);
                    new Thread(st).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class SocketThread implements Runnable {

        Socket socket;

        public SocketThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    Object inobj = in.readObject();

                    log("Einkommendes Paket");

                    if (inobj instanceof String[]) {
                        if (((String[]) inobj).length < 3) {
                            log("Datenpaket hat unerwartete Länge");
                            return;
                        }

                        String[] msg = (String[]) inobj;
                        System.out.print("[LOG] Paketinhalt: ");
                        for (String i : msg) {
                            System.out.print(i + " ");
                        }
                        System.out.print("\n");

                        switch (msg[0]) {
                            case "MSG":
                                log("Paket von " + socket.getInetAddress().toString() + ": " + msg[0] + " mit " + msg[2]);
                                sendToAllClients(msg);
                                break;
                            case "CMD":
                                if (msg[2].equals("REG")) {
                                    clients.add(socket);
                                    clientnames.put(socket, msg[1]);
                                    log("Client " + socket.getInetAddress().toString() + " registered as " + msg[1]);
                                }
                                break;
                        }
                    }
                } catch (IOException e) {
                    log("Client " + socket.getInetAddress().toString() + " bzw. " + clientnames.get(socket) + " hat die Verbindung getrennt!");
                    clients.remove(socket);
                    clientnames.remove(socket);
                    return;
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
