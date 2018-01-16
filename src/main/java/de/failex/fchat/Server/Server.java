package de.failex.fchat.Server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class Server {

    ServerSocket mainserver;
    Thread serverthread = null;

    int port;
    //TODO Should be user definable
    final int MAXCLIENTS = 32;
    int clientcount = 0;

    ArrayList<Socket> clients = new ArrayList<>();
    HashMap<Socket, String> clientnames = new HashMap<>();

    public Server(int port) {
        this.port = port;
        log("Server erfolgreich gestartet, es wird auf Verbindungen an Port " + port + " gewartet...");
        this.serverthread = new Thread(new ServerThread());
        this.serverthread.start();
    }


    /**
     * Sends a package (String Array) to all connected sockets
     *
     * @param msg the datapackage
     */
    private void sendToAllClients(String[] msg) {
        for (Socket client : clients) {
            //if (msg[0].equals("MSG") && msg[1].isEmpty() && msg[2].contains(clientnames.get(client))) continue;
            sendToClient(client, msg);
        }
    }

    /**
     * Sends a datapackage (String array) to given socket
     *
     * @param client where to send the datapackage to
     * @param msg    what datapackage to send
     *               <p>
     *               Structure of a datapackage:
     *               pkg[0] = MSG / CMD: Whether the content is a normal chat message or a command
     *               pkg[1] = nickname: Sends the nickname of the sender. empty if a command gets send
     *               pkg[2] = content of MSG or CMD / the chat message itself or the command to send
     *               </p>
     */
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

    /**
     * The main server thread
     */
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

    /**
     * socket thread that receives packets and then processed
     */
    class SocketThread implements Runnable {

        Socket socket;

        public SocketThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    Object inobj = in.readObject();

                    log("Incoming package");

                    if (inobj instanceof String[]) {
                        if (((String[]) inobj).length < 3) {
                            log("unexpected package length");
                            return;
                        }

                        String[] msg = (String[]) inobj;
                        System.out.print("[LOG] package contents: ");
                        for (String i : msg) {
                            System.out.print(i + " ");
                        }
                        System.out.print("\n");

                        if (msg[0].equals("MSG")) {
                            log("datapackage from " + socket.getInetAddress().toString() + ": " + msg[0] + " with " + msg[2]);
                            sendToAllClients(msg);

                        } else if (msg[0].equals("CMD")) {
                            if (msg[2].equals("REG")) {
                                //Check if nickname is already assigned to anyone
                                if (clientnames.containsValue(msg[1])) {
                                    log("Client " + socket.getInetAddress().toString() + " tried to register as " + msg[1] + " but the nickname is already assigned");
                                    sendToClient(socket, new String[]{"CMD","", "NICK"});
                                    return;
                                }
                                if (clientcount == MAXCLIENTS) {
                                    log("Client " + socket.getInetAddress().toString() + " tried to join but the room is full");
                                    sendToClient(socket, new String[]{"CMD", "", "FULL"});
                                } else {
                                    clientcount++;
                                    clients.add(socket);
                                    clientnames.put(socket, msg[1]);
                                    log("Client " + socket.getInetAddress().toString() + " registered as " + msg[1]);
                                    sendToAllClients(new String[]{"MSG", "", msg[1] + " joined the chat room!"});
                                }
                            }

                        }
                    }
                } catch (IOException e) {
                    log("Client " + socket.getInetAddress().toString() + " aka " + clientnames.get(socket) + " disconnected!");
                    sendToAllClients(new String[]{"MSG", "", clientnames.get(socket) + " left the chat room"});
                    clients.remove(socket);
                    clientnames.remove(socket);
                    clientcount--;
                    return;
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
