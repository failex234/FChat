package de.failex.fchat.Server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class Server {

    private ServerSocket mainserver;
    private Thread serverthread = null;
    private Thread interpreter = null;

    int port;
    //TODO Should be user definable
    private int maxclients;
    private int clientcount = 0;

    private ArrayList<Socket> clients = new ArrayList<>();
    private HashMap<Socket, String> clientnames = new HashMap<>();

    private File config = new File("config.json");
    private ServerConfig cfg;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String motd = "";
    private ArrayList<String> mods;
    private HashMap<String, String> modpasswords;

    public Server(int port) {
        if (config.exists()) {
            log("Config found, reading config");

            try (BufferedReader reader = new BufferedReader(new FileReader(config))) {
                StringBuilder sb = new StringBuilder();
                String line = reader.readLine();

                while (line != null) {
                    sb.append(line + "\n");
                    line = reader.readLine();
                }
                cfg = gson.fromJson(sb.toString(), ServerConfig.class);
                motd = cfg.getMotd();
                mods = cfg.getMods();
                maxclients = cfg.getMaxclients();
            } catch (IOException e) {
                log("Error reading config");
                e.printStackTrace();
            }

        } else {
            log("No config found. creating new config...");
            cfg = new ServerConfig();
            cfg.setMotd("");

            String json = gson.toJson(cfg);

            try {
                config.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(config), "utf-8"))) {
                writer.write(json);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.port = port;
        log("Server started successfully! listening for connections on port " + port);
        this.interpreter = new Thread(new InterpreterThread());
        interpreter.start();
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
            //TODO \/
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
        ObjectOutputStream out;
        try {
            out = new ObjectOutputStream(client.getOutputStream());
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            //Client disconnected
            //e.printStackTrace();
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
            Socket socket;
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

                    //DEBUG
                    //log("Incoming package");

                    if (inobj instanceof String[]) {
                        if (((String[]) inobj).length < 3) {
                            log("unexpected package length");
                            return;
                        }

                        String[] msg = (String[]) inobj;
                        //DEBUG
                        //System.out.print("[LOG] package contents: ");
                        //for (String i : msg) {
                        //    System.out.print(i + " ");
                        //}
                        //System.out.print("\n");

                        if (msg[0].equals("MSG")) {
                            log("datapackage from " + socket.getInetAddress().toString() + ": " + msg[0] + " with " + msg[2]);
                            sendToAllClients(msg);

                        } else if (msg[0].equals("CMD")) {
                            if (msg[2].equals("REG")) {
                                //Check if nickname is already assigned to anyone
                                if (clientnames.containsValue(msg[1])) {
                                    log("Client " + socket.getInetAddress().toString() + " tried to register as " + msg[1] + " but the nickname is already assigned");
                                    sendToClient(socket, new String[]{"CMD", "", "NICK"});
                                    return;
                                }
                                if (clientcount >= maxclients) {
                                    log("Client " + socket.getInetAddress().toString() + " tried to join but the room is full");
                                    sendToClient(socket, new String[]{"CMD", "", "FULL"});
                                } else {
                                    clientcount++;
                                    clients.add(socket);
                                    clientnames.put(socket, msg[1]);
                                    //Send motd to client if motd is set
                                    if (!motd.isEmpty()) {
                                        sendToClient(socket, new String[]{"MSG", "", motd});
                                    }
                                    log("Client " + socket.getInetAddress().toString() + " registered as " + msg[1]);
                                    sendToAllClients(new String[]{"MSG", "", msg[1] + " joined the chat room!"});
                                }
                            }

                        }
                    }
                } catch (IOException e) {
                    if (clients.contains(socket)) {
                        log("Client " + socket.getInetAddress().toString() + " aka " + clientnames.get(socket) + " disconnected!");
                        sendToAllClients(new String[]{"MSG", "", clientnames.get(socket) + " left the chat room"});
                        clients.remove(socket);
                        clientnames.remove(socket);
                        clientcount--;
                    }
                    return;
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class InterpreterThread implements Runnable {

        @Override
        public void run() {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while (!Thread.currentThread().isInterrupted()) {
                String cmd = "";
                try {
                    cmd = br.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String[] cmds = cmd.split(" ");
                cmd = cmds[0];
                switch (cmd) {
                    case "kick":
                        if (cmds.length <= 1) {
                            System.out.println("Whom do you want to kick?");
                        } else {
                            String name = cmds[1];
                            boolean kicked = false;
                            for (Socket s : clients) {
                                if (clientnames.get(s).equals(name)) {
                                    kicked = true;
                                    sendToClient(s, new String[]{"CMD", "", "KICK"});
                                    clientcount--;
                                    clients.remove(s);
                                    clientnames.remove(s);
                                    try {
                                        s.close();
                                        System.out.println("Kicked client " + name);
                                        break;
                                    } catch (IOException ignored) {

                                    }
                                }
                            }
                            if (!kicked) {
                                System.out.println("Client " + name + " not found!");
                            }
                        }
                        break;
                    case "online":
                        if (clientcount == 1)
                            System.out.println("There is currently 1 client connected\nThe following client is connected");
                        else
                            System.out.println("There are currently " + clientcount + " clients connected\nThe following clients are connected");

                        for (Socket s : clients) {
                            System.out.print(clientnames.get(s) + " ");
                        }
                        if (clients.size() == 0) System.out.print("nobody");
                        break;
                    case "addmod":
                        break;
                    case "removemod":
                        break;
                    case "quit":
                    case "stop":
                        //TODO: Fix config saving
                        //Save current settings / options and write them to the config
                        cfg.setMaxclients(maxclients);
                        cfg.setMotd(motd);
                        cfg.setMods(mods);
                        cfg.setModpasswords(modpasswords);

                        String newjson = gson.toJson(cfg);

                        try {
                            PrintWriter pw = new PrintWriter(config);
                            pw.write(newjson);
                            pw.close();
                        } catch (IOException e) {
                            log("Error while saving config!!");
                            e.printStackTrace();
                        }
                        System.exit(0);
                        break;
                    case "setmotd":
                        if (cmds.length <= 1) {
                            System.out.println("What should the new motd look like?");
                        } else {
                            StringBuilder newmotd = new StringBuilder();
                            for (int i = 0; i < cmds.length; i++) {
                                if (cmds.length - 1 == i) {
                                    newmotd.append(cmds[i]);
                                } else {
                                    newmotd.append(cmds[i]).append(" ");
                                }
                            }

                            motd = newmotd.toString();
                            System.out.println("Successfully set motd to \"" + newmotd.toString() + "\"!");
                        }
                        break;
                    case "setclientlimit":
                        if (cmds.length <= 1) {
                            System.out.println("What is the new limit?");
                        } else {
                            int newcount;
                            try {
                                newcount = Integer.parseInt(cmds[1]);
                            } catch (NumberFormatException e) {
                                System.out.println("This is not a number!");
                                break;
                            }
                            if (clientcount > newcount) {
                                System.out.println("Warning: The max player count is over the limit!");
                            }
                            clientcount = newcount;
                            System.out.println("Successfully changed the client limit to " + newcount + "!");
                        }
                        break;
                    case "help":
                        System.out.println("current available commands:");
                        System.out.println("online");
                        System.out.println("kick <nickname>");
                        System.out.println("addmod <nickname>");
                        System.out.println("removemod <nickname>");
                        System.out.println("quit / stop");
                        System.out.println("setmotd <newmotd>");
                        System.out.println("setclientlimit <newlimit>");
                        System.out.println("help");
                        break;
                    default:
                        System.out.println("Command not found!");
                        break;
                }
            }
        }
    }
}
