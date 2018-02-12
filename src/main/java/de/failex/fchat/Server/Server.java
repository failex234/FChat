package de.failex.fchat.Server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class Server {

    private ServerSocket mainserver;
    private Thread serverthread = null;
    private Thread interpreter = null;

    int port;
    //TODO Should be user definable
    private int maxclients;
    private int clientcount = 0;
    private int passwordsize = 8;

    private ArrayList<Socket> clients = new ArrayList<>();
    private HashMap<Socket, String> clientnames = new HashMap<>();

    private File config = new File("config.json");
    private ServerConfig cfg;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String motd = "";
    private ArrayList<String> mods;
    private HashMap<String, String> modpasswords;
    private HashMap<String, Boolean> loggedinmods;

    public Server(int port) {
        modpasswords = new HashMap<>();
        loggedinmods = new HashMap<>();
        if (config.exists()) {
            log("Config found, reading config");
            System.out.printf("");

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

    public String generatePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_/";
        Random rnd = new Random();
        StringBuilder temp = new StringBuilder();

        for (int i = 0; i < passwordsize; i++) temp.append(chars.charAt(rnd.nextInt(chars.length())));

        return temp.toString();

    }

    public boolean isMod(Socket s) {
        return mods.contains(s);
    }

    public String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(password.getBytes());

            byte byteData[] = md.digest();

            //convert the byte to hex format method 1
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < byteData.length; i++) {
                sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
     * socket thread that receives the packets and processes them
     */
    class SocketThread implements Runnable {

        Socket socket;

        SocketThread(Socket socket) {
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
                            } else if (msg[2].equals("REGMOD")) {
                                if (!clientnames.containsValue(msg[1])) {
                                    log("Client " + socket.getInetAddress().toString() + " tried to login as a mod before logging normally before");
                                    sendToClient(socket, new String[]{"CMD", "", "NOTREG"});
                                } else {
                                    if (isMod(socket)) {
                                        if (msg.length < 4) {
                                            log("Client " + socket.getInetAddress().toString() + " tried to login as a mod but failed to send a password");
                                            sendToClient(socket, new String[]{"CMD", "", "NOPASSWD"});
                                        } else {
                                            if (!loggedinmods.get(msg[1])) {
                                                if (modpasswords.get(msg[1]).equals(msg[3])) {
                                                    log("Client " + socket.getInetAddress().toString() + " logged in as a mod!");
                                                    loggedinmods.put(msg[1], true);
                                                    sendToClient(socket, new String[]{"CMD", "", "REGSUCCESS"});
                                                } else {
                                                    log("Client " + socket.getInetAddress().toString() + " tried to login as a mod but has a wrong password!");
                                                    sendToClient(socket, new String[]{"CMD", "", "WRONGPASSWD"});
                                                }
                                            } else {
                                                log("Client " + socket.getInetAddress().toString() + " tried to login as a mod even though he is already logged in");
                                                sendToClient(socket, new String[]{"CMD", "", "ALREADYLOGGED"});
                                            }
                                        }
                                    } else {
                                        log("Client " + socket.getInetAddress().toString() + " tried to login as a mod even though he is no mod");
                                        sendToClient(socket, new String[]{"CMD", "", "NOMOD"});
                                    }
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
                            System.out.println("There is currently 1 client out of " + maxclients + " connected\nThe following client is connected");
                        else
                            System.out.println("There are currently " + clientcount + " clients out of " + maxclients + " connected\nThe following clients are connected");

                        for (Socket s : clients) {
                            System.out.print(clientnames.get(s) + " ");
                        }
                        if (clients.size() == 0) System.out.println("nobody");
                        break;
                    case "addmod":
                        if (cmds.length <= 1) {
                            System.out.println("Whom do you want to add as a mod?");
                        } else {
                            String name = cmds[1];
                            boolean modded = false;
                            for (Socket s : clients) {
                                if (clientnames.get(s).equals(name)) {
                                    if (isMod(s)) {
                                        System.out.println(name + " is already a mod!");
                                        break;
                                    } else {
                                        modded = true;
                                        System.out.println(name + " is now a mod!");
                                        String password = generatePassword();
                                        System.out.println(password);
                                        System.out.println(hashPassword(password));
                                        sendToClient(s, new String[]{"CMD", "", "MODC", password});
                                        mods.add(name);
                                        modpasswords.put(name, hashPassword(password));
                                        break;
                                    }
                                }
                            }
                            if (!modded) {
                                System.out.println(name + " not found!");
                            }
                        }
                        break;
                    case "removemod":
                        if (cmds.length <= 1) {
                            System.out.println("Whom do you want to remove as a mod?");
                        } else {
                            String name = cmds[1];
                            boolean userfound = false;

                            for (Socket s : clients) {
                                if (clientnames.get(s).equals(name)) {
                                    if (!isMod(s)) {
                                        System.out.println(name + " is no mod!");
                                        userfound = true;
                                    } else {
                                        mods.remove(s);
                                        sendToClient(s, new String[]{"CMD", "", "MODR"});
                                        System.out.println(name + " is no longer a mod!");
                                        userfound = true;
                                    }
                                }
                            }

                            if (!userfound) System.out.println("User " + name + " not found!");
                        }
                        break;
                    case "quit":
                    case "stop":
                        //Save current settings / options and write them to the config
                        cfg.setMaxclients(maxclients);
                        cfg.setMotd(motd);
                        cfg.setMods(mods);
                        cfg.setModpasswords(modpasswords);
                        cfg.setPasswordsize(passwordsize);

                        String newjson = gson.toJson(cfg);

                        config.delete();

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
                            for (int i = 1; i < cmds.length; i++) {
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
                            if (clientcount < 1) {
                                System.out.println("Error: This number is too low!");
                                break;
                            }
                            if (clientcount > newcount) {
                                System.out.println("Warning: The max player count is over the limit!");
                            }
                            maxclients = newcount;
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
