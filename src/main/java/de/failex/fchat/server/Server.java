package de.failex.fchat.server;

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

    private ArrayList<String> bannedclients = new ArrayList<>();

    private File config = new File("config.json");
    private ServerConfig cfg;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String motd = "";
    private ArrayList<String> mods = new ArrayList<>();
    private HashMap<String, String> modpasswords = new HashMap<>();
    private HashMap<String, Boolean> loggedinmods = new HashMap<>();

    public Server(int port) {
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
                modpasswords = cfg.getModpasswords();
                bannedclients = cfg.getBannedclients();
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
        logf("Server started successfully! listening for connections on port %d", port);
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

    public boolean isMod(String s) {
        return mods.contains(s);
    }

    public boolean isMod(Socket s) {
        return isMod(clientnames.get(s));
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

    /**
     * Log to the console
     *
     * @param msg the message to log
     */
    private void log(String msg) {
        System.out.println("[LOG] " + msg);
    }

    /**
     * Log a formatted message to the console
     *
     * @param format the format to print
     * @param objs   the objects to insert into the format
     */
    private void logf(String format, Object... objs) {
        System.out.printf("[LOG] " + format + "\n", objs);
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

                            //Check if client is registered
                            if (!clients.contains(socket)) {
                                logf("%s (%s) tried to chat but is not registered!", "nonick", socket.getInetAddress().toString());
                                sendToClient(socket, new String[]{"CMD", "", "NOTCONNECTED"});
                                return;
                            }
                            if (msg[2].startsWith("/")) {
                                String[] cmd = msg[2].split(" ");

                                logf("%s (%s) executed command %s", clientnames.get(socket), socket.getInetAddress().toString(), cmd[0]);
                                //Client commands
                                switch (cmd[0]) {
                                    case "/help":
                                        //Send help menu based on if client is mod
                                        break;
                                    case "/ban":
                                        if (!isMod(socket)) {
                                            sendToClient(socket, new String[]{"MSG", "", "You're not a mod!"});
                                            break;
                                        }

                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot an argument!"});
                                            break;
                                        }

                                        if (banClient(cmd[1], false)) {
                                            sendToClient(socket, new String[]{"MSG", "", "Banned client " + cmd[1]});
                                        } else {
                                            sendToClient(socket, new String[]{"MSG", "", "Can't ban " + cmd[1]});
                                        }
                                        break;
                                    case "/kick":
                                        if (!isMod(socket)) {
                                            sendToClient(socket, new String[]{"MSG", "", "You're not a mod!"});
                                            break;
                                        }

                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot an argument!"});
                                            break;
                                        }

                                        if (kickClient(cmd[1], false)) {
                                            sendToClient(socket, new String[]{"MSG", "", "Kicked " + cmd[1]});
                                        } else {
                                            sendToClient(socket, new String[]{"MSG", "", "Can't kick " + cmd[1]});
                                        }
                                        break;
                                    case "/addmod":
                                        //Check if mod
                                        if (!isMod(socket)) {
                                            sendToClient(socket, new String[]{"MSG", "", "You're not a mod!"});
                                            break;
                                        }

                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot an argument!"});
                                            break;
                                        }

                                        if (isMod(cmd[1])) {
                                            sendToClient(socket, new String[]{"MSG", "", cmd[1], " is already a mod!"});
                                        } else {
                                            sendToClient(socket, new String[]{"MSG", "", "added " + cmd[1] + " as a mod"});
                                        }
                                        break;
                                    case "/online":
                                        sendToClient(socket, new String[]{"MSG", "", "There are currently " + clientcount + " clients out of " + maxclients + " client connected!"});
                                        break;
                                    case "/msg":
                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot an argument!"});
                                            break;
                                        }

                                        Socket receiver = getSocket(cmd[1]);
                                        if (receiver == null) {
                                            sendToClient(socket, new String[]{"MSG", "", "Player not found"});
                                            break;
                                        }
                                        //Missing message
                                        if (cmd.length < 3) {
                                            sendToClient(socket, new String[]{"MSG", "", "You are missing a message"});
                                            break;
                                        }

                                        String endstring = "";
                                        for (int i = 2; i < cmd.length; i++) {
                                            endstring += cmd[i] + " ";
                                        }

                                        sendToClient(receiver, new String[]{"MSG", "", "Message from " + clientnames.get(socket) + ": " + endstring});
                                        sendToClient(socket, new String[]{"MSG", "", "Sent message to " + cmd[1]});
                                        break;
                                    default:
                                        sendToClient(socket, new String[]{"MSG", "", "Command not found!"});
                                        break;
                                }
                            } else

                                //Only send message when client didn't spam newlines
                                if (getCount(msg[2], '\n') < 2) {
                                    logf("Message from %s (%s): %s", msg[1], socket.getInetAddress().toString(), msg[2]);
                                    sendToAllClients(msg);
                                }

                        } else if (msg[0].equals("CMD")) {
                            if (msg[2].equals("REG")) {
                                //Check if user is banned
                                if (bannedclients.contains(msg[1])) {
                                    logf("%s tried to register but got kicked because he's banned!", msg[1]);
                                    sendToClient(socket, new String[]{"CMD", "", "BAN"});
                                }
                                //Check if nickname is already assigned to anyone
                                if (clientnames.containsValue(msg[1])) {
                                    logf("Client %s tried to register as %s but the nickname is already assigned", socket.getInetAddress().toString(), msg[1]);
                                    sendToClient(socket, new String[]{"CMD", "", "NICK"});
                                    return;
                                }
                                if (clientcount >= maxclients) {
                                    logf("Client %s (%s) tried to join but the room is full", msg[1], socket.getInetAddress().toString());
                                    sendToClient(socket, new String[]{"CMD", "", "FULL"});
                                } else {
                                    clientcount++;
                                    clients.add(socket);
                                    clientnames.put(socket, msg[1]);
                                    //Send motd to client if motd is set
                                    if (!motd.isEmpty()) {
                                        sendToClient(socket, new String[]{"MSG", "", motd});
                                    }
                                    logf("Client %s registered as %s", socket.getInetAddress().toString(), msg[1]);
                                    sendToAllClients(new String[]{"MSG", "", msg[1] + " joined the chat room!"});

                                    //Send Mod login request to client
                                    if (mods.contains(msg[1])) sendToClient(socket, new String[]{"CMD", "", "MODL"});
                                }
                            } else if (msg[2].equals("REGMOD")) {
                                if (!clientnames.containsValue(msg[1])) {
                                    logf("Client %s (%s) tried to login as a mod before logging in normally before", msg[1], socket.getInetAddress().toString());
                                    sendToClient(socket, new String[]{"CMD", "", "NOTREG"});
                                } else {
                                    if (isMod(msg[1])) {
                                        if (msg.length < 4 || msg[3].isEmpty()) {
                                            logf("Client %s (%s) tried to login as a mod but failed to send a password", msg[1], socket.getInetAddress().toString());
                                            sendToClient(socket, new String[]{"CMD", "", "NOPASSWD"});
                                        } else {
                                            if (!loggedinmods.containsKey(msg[1])) {
                                                if (modpasswords.get(msg[1]).equals(hashPassword(msg[3]))) {
                                                    logf("Client %s (%s) logged in as a mod", msg[1], socket.getInetAddress().toString());
                                                    loggedinmods.put(msg[1], true);
                                                    sendToClient(socket, new String[]{"CMD", "", "REGSUCCESS"});
                                                } else {
                                                    logf("Client %s (%s) tried to login as a mod but has a wrong password!", msg[1], socket.getInetAddress().toString());
                                                    sendToClient(socket, new String[]{"CMD", "", "WRONGPASSWD"});
                                                }
                                            } else {
                                                logf("Client %s (%s) tried to login as a mod even though he already is logged in", msg[1], socket.getInetAddress());
                                                sendToClient(socket, new String[]{"CMD", "", "ALREADYLOGGED"});
                                            }
                                        }
                                    } else {
                                        logf("Client %s (%s) tried to login as a mod even though he is no mod", msg[1], socket.getInetAddress().toString());
                                        sendToClient(socket, new String[]{"CMD", "", "NOMOD"});
                                    }
                                }
                            }

                        }
                    }
                } catch (IOException e) {
                    if (clients.contains(socket)) {
                        logf("Client %s (%s) disconnected!", clientnames.get(socket), socket.getInetAddress().toString());
                        sendToAllClients(new String[]{"MSG", "", clientnames.get(socket) + " left the chat room"});

                        if (isMod(clientnames.get(socket))) {
                            loggedinmods.remove(clientnames.get(socket));
                        }
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
                            kickClient(cmds[1], true);
                        }
                        break;
                    case "online":
                        if (clientcount == 1)
                            System.out.printf("There is currently 1 client out of %d connected\nThe following client is connected\n", maxclients);
                        else
                            System.out.printf("There are currently %d clients out of %d connected\nThe following clients are connected\n", clientcount, maxclients);

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
                                    if (isMod(clientnames.get(s))) {
                                        System.out.printf("%s is already a mod!\n", name);
                                        break;
                                    } else {
                                        modded = true;
                                        System.out.printf("%s is now a mod!\n", name);
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
                                System.out.printf("%s not found!\n", name);
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
                                    if (!isMod(clientnames.get(s))) {
                                        System.out.printf("%s is no mod!\n", name);
                                        userfound = true;
                                    } else {
                                        mods.remove(name);
                                        modpasswords.remove(name);
                                        loggedinmods.remove(name);
                                        sendToClient(s, new String[]{"CMD", "", "MODR"});
                                        System.out.printf("%s is no longer a mod!\n", name);
                                        userfound = true;
                                    }
                                }
                            }

                            if (mods.contains(name)) {
                                mods.remove(name);
                                modpasswords.remove(name);
                                loggedinmods.remove(name);
                                System.out.printf("%s is no longer a mod!\n", name);
                                userfound = true;
                            }

                            if (!userfound) System.out.printf("User %s not found!\n", name);
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
                        cfg.setBannedclients(bannedclients);

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
                            System.out.printf("Successfully set motd to \"%s\"!\n", newmotd.toString());
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
                            if (newcount < 1) {
                                System.out.println("Error: This number is too low!");
                                break;
                            }
                            if (clientcount > newcount) {
                                System.out.println("Warning: The max player count is over the limit!");
                            }
                            maxclients = newcount;
                            System.out.printf("Successfully changed the client limit to %d!\n", newcount);
                        }
                        break;
                    case "getclientlimit":
                        System.out.printf("The client limit is set to %d clients\n", maxclients);
                        break;
                    case "getmodlist":
                        if (mods.size() == 0) {
                            System.out.println("No one is a mod. Add some with addmod <nickname>!");
                        } else {
                            for (String client : mods) {
                                System.out.println(client);
                            }
                        }
                        break;
                    case "ismod":
                        if (cmds.length <= 1) {
                            System.out.println("Whom do you want to check?");
                        } else {
                            if (isMod(cmds[1])) System.out.printf("%s is a mod\n", cmds[1]);
                            else System.out.printf("%s is not a mod\n", cmds[1]);
                        }
                        break;
                    case "ban":
                        if (cmds.length <= 1) {
                            System.out.println("Whom do you want to ban?");
                        } else {
                            banClient(cmds[1], true);
                        }
                        break;
                    case "unban":
                        if (cmds.length <= 1) {
                            System.out.println("Whom do you want to unban?");
                        } else {
                            String name = cmds[1];
                            if (bannedclients.contains(name)) {
                                bannedclients.remove(name);
                                System.out.printf("%s is no longer banned!\n", name);
                            } else {
                                System.out.printf("%s is not banned!\n", name);
                            }
                        }
                        break;
                    case "getmodpasswords":
                        if (modpasswords.isEmpty()) System.out.println("No Modpasswords saved!");
                        for (String key : modpasswords.keySet()) {
                            System.out.printf("%s -> %s", key, modpasswords.get(key));
                        }
                        break;
                    case "help":
                        System.out.println("current available commands:");
                        System.out.println("online");
                        System.out.println("kick <nickname>");
                        System.out.println("ban <nickname>");
                        System.out.println("unban <nickname>");
                        System.out.println("addmod <nickname>");
                        System.out.println("removemod <nickname>");
                        System.out.println("ismod <nickname>");
                        System.out.println("isbanned <nickname>");
                        System.out.println("getmodlist");
                        System.out.println("getmodpasswords");
                        System.out.println("quit / stop");
                        System.out.println("setmotd <newmotd>");
                        System.out.println("setclientlimit <newlimit>");
                        System.out.println("getclientlimit");
                        System.out.println("help");
                        break;
                    default:
                        System.out.println("Command not found!");
                        break;
                }
            }
        }
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

    /**
     * Checks if a client is connected
     *
     * @param client the client to check
     * @return true if client is connected otherwise false
     */
    private boolean isConnected(String client) {
        return clientnames.containsValue(client);
    }

    /**
     * Checks if a client is connected
     *
     * @param client the client to check
     * @return true if client is connected otherwise false
     */
    private boolean isConnected(Socket client) {
        return clients.contains(client);
    }

    /**
     * Search for an online client
     *
     * @param name The name of the online client
     * @return socket of the client if client is online otherwise null
     */
    private Socket getSocket(String name) {
        for (Socket s : clientnames.keySet()) {
            if (clientnames.get(s).equals(name)) return s;
        }

        return null;
    }

    /**
     * Ban a client
     *
     * @param name The client to ban
     * @param log  Log to console or not
     */
    private boolean banClient(String name, boolean log) {
        if (bannedclients.contains(name)) {
            System.out.printf("%s is already banned!\n", name);
            return false;
        } else {
            if (isConnected(name)) {
                for (Socket s : clients) {
                    if (clientnames.get(s).equals(name)) {
                        sendToClient(s, new String[]{"CMD", "", "BAN"});
                        clientcount--;
                        clients.remove(s);
                        clientnames.remove(s);
                        try {
                            s.close();
                            if (log) System.out.printf("Banned client %s\n", name);
                            bannedclients.add(name);
                            return true;
                        } catch (IOException ignored) {
                            return false;
                        }
                    }
                }
            } else {
                bannedclients.add(name);
                if (log) System.out.printf("Banned client %s\n", name);
                return true;
            }
            return false;
        }
    }

    /**
     * Kicks a client
     *
     * @param name The client to kick
     * @param log  Log to console or not
     */
    private boolean kickClient(String name, boolean log) {
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
                    if (log) System.out.printf("Kicked client %s\n", name);
                    return true;
                } catch (IOException ignored) {

                }
            }
        }
        if (!kicked) {
            if (log) System.out.printf("Client %s not found!\n", name);
            return false;
        }
        return false;
    }

}