package de.failex.fchat.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.failex.fchat.CommonUtils;
import de.failex.fchat.Cryptography;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

public class Server {

    private ServerSocket mainserver;
    private Thread serverthread = null;
    private Thread interpreter = null;
    private Thread timeouthread = null;

    int port;
    private int maxclients = 32;
    protected static int clientcount = 0;

    protected static ArrayList<ConnectedClient> clients = new ArrayList<>();

    private ArrayList<String> bannedclients = new ArrayList<>();

    private File config = new File(("data" + File.separator + "servercfg.json").replace(" ", ""));
    private File datafolder = new File("data");
    private File lockfile = new File(("data" + File.separator + "fchat.lock").replace(" ", ""));
    private ServerConfig cfg;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String motd = "";
    private ArrayList<UUID> moduuids = new ArrayList<>();
    private final int SERVERPROTVERSION = 2;

    private PublicKey pub;
    private PrivateKey priv;

    public Server(int port, boolean forcelaunch) {
        if (lockfile.exists() && !forcelaunch) {
            System.err.println("[ERR] An instance of FChat is already running! Refusing to launch.");
            System.err.println("Or you maybe did not quit the server properly. If you believe this is an error");
            System.err.println("or you're 100% sure that no instance is running try rerunning the server with the --force flag");
            System.err.println("ex. FChat 1152 --force");
            System.exit(1);
        } else {
            int pid = CommonUtils.getPID();
            try {
                datafolder.mkdir();
                lockfile.createNewFile();
                PrintWriter pw = new PrintWriter(lockfile);
                pw.println(pid);
                pw.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                System.err.println("Can't create lockfile. Please make sure the permissions are set correctly!");
            }
        }
        //TODO Save server config on change
        if (datafolder.exists() && datafolder.isDirectory() && config.exists()) {
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
                maxclients = cfg.getMaxclients();
                bannedclients = cfg.getBannedclients();
                moduuids = cfg.getModuuids();

            } catch (IOException e) {
                log("Error reading config");
                e.printStackTrace();
            }

        } else {
            log("No config found. creating new config...");
            datafolder.mkdir();
            cfg = new ServerConfig();
            cfg.setMotd("");

            try {
                config.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            saveConfig();
        }

        try {
            if (!Cryptography.keysExist()) {
                KeyPair temp = Cryptography.generateKeyPair();
                Cryptography.saveKeys(temp.getPublic(), temp.getPrivate());
            }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }

        this.port = port;
        logf("Server started successfully! listening for connections on port %d", port);
        this.interpreter = new Thread(new InterpreterThread());
        this.serverthread = new Thread(new ServerThread());
        this.timeouthread = new Thread(new TimeoutThread());
        this.serverthread.start();
        this.interpreter.start();
        this.timeouthread.start();
    }

    //TODO: Remove user from mod arrarylist on disconnect

    /**
     * Sends a package (String Array) to all connected sockets
     *
     * @param msg the datapackage
     */
    private void sendToAllClients(String[] msg) {
        for (ConnectedClient client : clients) {
            //TODO \/
            //if (msg[0].equals("MSG") && msg[1].isEmpty() && msg[2].contains(clientnames.get(client))) continue;
            sendToClient(client.getSocket(), msg);
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
            clientcount--;
            clients.remove(getClient(client));
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

    public void saveConfig() {
        //Save current settings / options and write them to the config
        cfg.setMaxclients(maxclients);
        cfg.setMotd(motd);
        cfg.setBannedclients(bannedclients);
        cfg.setModuuids(moduuids);

        String json = gson.toJson(cfg);

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(config), "utf-8"))) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
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

                    //DEBUGfor (Socket s : clientnames.keySet())
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
                            if (!isConnected(socket)) {
                                logf("%s (%s) tried to chat but is not registered.", "nonick", socket.getInetAddress().toString());
                                sendToClient(socket, new String[]{"CMD", "", "NOTCONNECTED"});
                                return;
                            }
                            ConnectedClient sender = getClient(socket);
                            if (msg[3].startsWith("/")) {
                                String[] cmd = msg[3].split(" ");

                                logf("%s (%s) executed command %s", sender.getNickname(), sender.getRemoteAddress(), cmd[0]);
                                //Client commands
                                switch (cmd[0]) {
                                    case "/help":
                                        //Send help menu based on if client is mod
                                        sendToClient(socket, new String[]{"MSG", "", "Available commands"});
                                        sendToClient(socket, new String[]{"MSG", "", "/msg <name> <message> - Send a private message to <name>"});
                                        sendToClient(socket, new String[]{"MSG", "", "/online - See who's online"});
                                        sendToClient(socket, new String[]{"MSG", "", "/disconnect - Disconnect securely from the server"});
                                        if (sender.isModerator()) {
                                            sendToClient(socket, new String[]{"MSG", "", "/ban <name> - Ban <name>"});
                                            sendToClient(socket, new String[]{"MSG", "", "/kick <name> - Kick <name>"});
                                            sendToClient(socket, new String[]{"MSG", "", "/addmod <name> - Add <name> as a mod"});
                                            sendToClient(socket, new String[]{"MSG", "", "/clear - Force a chat clear for everybody"});
                                            sendToClient(socket, new String[]{"MSG", "", "/broadcast <message> - Broadcasts a message"});
                                        }
                                        sendToClient(socket, new String[]{"MSG", "", "/help - Show this menu"});
                                        break;
                                    case "/ban":
                                        if (!sender.isModerator()) {
                                            sendToClient(socket, new String[]{"MSG", "", "You're not a mod."});
                                            break;
                                        }

                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot an argument."});
                                            break;
                                        }

                                        if (cmd[1].equals(sender.getNickname())) {
                                            sendToClient(socket, new String[]{"MSG", "", "You can't ban yourself."});
                                            break;
                                        }

                                        if (!isMod(getClient(cmd[1]).getUniqueId()) && banClient(cmd[1], false)) {
                                            sendToClient(socket, new String[]{"MSG", "", "Banned client " + cmd[1]});
                                        } else {
                                            sendToClient(socket, new String[]{"MSG", "", "Can't ban " + cmd[1]});
                                        }
                                        break;
                                    case "/kick":
                                        if (!sender.isModerator()) {
                                            sendToClient(socket, new String[]{"MSG", "", "You're not a mod."});
                                            break;
                                        }


                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot an argument."});
                                            break;
                                        }

                                        if (cmd[1].equals(sender.getNickname())) {
                                            sendToClient(socket, new String[]{"MSG", "", "You can't kick yourself."});
                                            break;
                                        }

                                        if (!isMod(getClient(cmd[1]).getUniqueId()) && kickClient(cmd[1], false)) {
                                            sendToClient(socket, new String[]{"MSG", "", "Kicked " + cmd[1]});
                                        } else {
                                            sendToClient(socket, new String[]{"MSG", "", "Can't kick " + cmd[1]});
                                        }
                                        break;
                                    case "/addmod":
                                        //Check if mod
                                        if (!sender.isModerator()) {
                                            sendToClient(socket, new String[]{"MSG", "", "You're not a mod."});
                                            break;
                                        }

                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot an argument."});
                                            break;
                                        }

                                        if (cmd[1].equals(sender.getNickname())) {
                                            sendToClient(socket, new String[]{"MSG", "", "You can't add yourself as a mod."});
                                            break;
                                        }

                                        if (isConnected(cmd[1])) {
                                            ConnectedClient newmod = getClient(cmd[1]);

                                            if (isMod(newmod.getUniqueId())) {
                                                sendToClient(socket, new String[]{"MSG", "", cmd[1], " is already a mod!"});
                                                break;
                                            }

                                            moduuids.add(newmod.getUniqueId());
                                            newmod.setModerator(true);
                                            sendToClient(newmod.getSocket(), new String[]{"MSG", "", sender.getNickname() + " added you as a moderator"});
                                            sendToClient(newmod.getSocket(), new String[]{"CMD", "", "MODC"});
                                            sendToClient(socket, new String[]{"MSG", "", "added " + cmd[1] + " as a mod"});
                                        } else {
                                            sendToClient(socket, new String[]{"MSG", "", cmd[1], " was not found!"});
                                        }
                                        break;
                                    case "/online":
                                        sendToClient(socket, new String[]{"MSG", "", "Currently there " + (clientcount > 1 ? "are " : "is ") + clientcount + " client" + (clientcount > 1 ? "s" : "") + " out of " + maxclients + " clients connected."});
                                        break;
                                    case "/msg":
                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot an argument."});
                                            break;
                                        }

                                        Socket receiver = getSocket(cmd[1]);
                                        if (receiver == null) {
                                            sendToClient(socket, new String[]{"MSG", "", "Client not found."});
                                            break;
                                        }

                                        //Don't let a client send a message to itself
                                        if (getClient(receiver).getNickname().equals(sender.getNickname())) {
                                            sendToClient(socket, new String[]{"MSG", "", "You can't send a message to yourself."});
                                            break;
                                        }

                                        //Missing message
                                        if (cmd.length < 3) {
                                            sendToClient(socket, new String[]{"MSG", "", "You are missing a message."});
                                            break;
                                        }

                                        String endstring = "";
                                        for (int i = 2; i < cmd.length; i++) {
                                            endstring += cmd[i] + " ";
                                        }

                                        sendToClient(receiver, new String[]{"MSG", "", "Message from " + sender.getNickname() + ": " + endstring});
                                        sendToClient(socket, new String[]{"MSG", "", "Sent message to " + cmd[1]});
                                        break;
                                    case "/disconnect":
                                        sendToClient(sender.getSocket(), new String[]{"CMD", "", "DISC"});
                                        clientcount--;
                                        clients.remove(sender);

                                        logf("Client %s (%s) disconnected\n", sender.getNickname(), sender.getRemoteAddress());
                                        sendToAllClients(new String[]{"MSG", "", sender.getNickname() + " left the chat room."});

                                        //Send new clientlist to all players
                                        String[] clientlist = Arrays.copyOf(getClientNames(), getClientNames().length, String[].class);
                                        sendToAllClients(ArrayUtils.addAll(new String[]{"CMD", "", "LIST"}, clientlist));
                                        break;
                                    case "/clear":
                                        //Check if mod
                                        if (!sender.isModerator()) {
                                            sendToClient(socket, new String[]{"MSG", "", "You're not a mod."});
                                            break;
                                        }

                                        sendToAllClients(new String[]{"CMD", "", "CLEAR"});
                                        sendToClient(socket, new String[]{"MSG", "", "Chat cleared"});

                                        break;
                                    case "/broadcast":
                                        //Check if mod
                                        if (!sender.isModerator()) {
                                            sendToClient(socket, new String[]{"MSG", "", "You're not a mod."});
                                            break;
                                        }

                                        if (cmd.length < 2) {
                                            sendToClient(socket, new String[]{"MSG", "", "You forgot a message to broadcast"});
                                            break;
                                        }

                                        String endstringbroad = "";
                                        for (int i = 1; i < cmd.length; i++) {
                                            endstringbroad += cmd[i] + " ";
                                        }

                                        sendToAllClients(new String[]{"MSG", "", endstringbroad});
                                        break;
                                    default:
                                        sendToClient(socket, new String[]{"MSG", "", "Command not found."});
                                        break;
                                }
                            } else

                                //Only send message when client didn't spam newlines
                                if (getCount(msg[2], '\n') < 2) {
                                    logf("Message from %s (%s): %s", msg[2], sender.getRemoteAddress(), msg[3]);

                                    //Remove UUID from package so that clients will never get to see the UUID
                                    String[] newpkg = new String[msg.length - 1];

                                    for (int i = 0; i < msg.length - 1; i++) {
                                        if (i >= 1) {
                                            newpkg[i] = msg[i + 1];
                                        } else {
                                            newpkg[i] = msg[i];
                                        }
                                    }
                                    if (getClient(socket).isModerator()) {
                                        newpkg[2] = "*MOD* > " + newpkg[2];
                                    } else {
                                        newpkg[2] = " > " + newpkg[2];
                                    }

                                    sendToAllClients(newpkg);
                                }

                        } else if (msg[0].equals("CMD")) {
                            if (msg[3].equals(SERVERPROTVERSION + "REG")) {
                                //Check if user is banned
                                if (bannedclients.contains(msg[2])) {
                                    logf("%s tried to register but got kicked because he's banned.", msg[2]);
                                    sendToClient(socket, new String[]{"CMD", "", "BAN"});
                                }
                                //Check if nickname is already assigned to anyone
                                if (isTaken(msg[2])) {
                                    logf("Client %s tried to register as %s but the nickname is already assigned", socket.getInetAddress().toString(), msg[1]);
                                    sendToClient(socket, new String[]{"CMD", "", "NICK"});
                                    return;
                                }
                                if (clientcount >= maxclients) {
                                    logf("Client %s (%s) tried to join but the room is full", msg[2], socket.getInetAddress().toString());
                                    sendToClient(socket, new String[]{"CMD", "", "FULL"});
                                } else {
                                    ConnectedClient newclient = new ConnectedClient(msg[2], UUID.fromString(msg[1]), socket);
                                    if (isMod(newclient.getUniqueId())) {
                                        newclient.setModerator(true);
                                        sendToClient(socket, new String[]{"CMD", "", "MODS"});
                                    }
                                    clientcount++;
                                    clients.add(newclient);
                                    //Send motd to client if motd is set
                                    if (!motd.isEmpty()) {
                                        sendToClient(socket, new String[]{"MSG", "", motd});
                                    }
                                    logf("Client %s registered as %s", newclient.getRemoteAddress(), msg[2]);
                                    sendToAllClients(new String[]{"MSG", "", msg[2] + " joined the chat room."});

                                    //Send new clientlist to all players
                                    String[] clientlist = Arrays.copyOf(getClientNames(), getClientNames().length, String[].class);
                                    sendToAllClients(ArrayUtils.addAll(new String[]{"CMD", "", "LIST"}, clientlist));

                                }
                            } else if (msg[2].equals("LIST")) {
                                String[] clients = Arrays.copyOf(getClientNames(), getClientNames().length, String[].class);
                                sendToClient(socket, ArrayUtils.addAll(new String[]{"CMD", "", "LIST"}, clients));
                            }

                        }
                    }
                } catch (IOException e) {
                    if (clients.contains(socket)) {
                        logf("Client %s (%s) disconnected.", getClient(socket).getNickname(), socket.getInetAddress().toString());
                        sendToAllClients(new String[]{"MSG", "", getClient(socket).getNickname() + " left the chat room"});

                        clients.remove(getClient(socket));
                        clientcount--;

                        //Send new clientlist to all players
                        String[] clientlist = Arrays.copyOf(getClientNames(), getClientNames().length, String[].class);
                        sendToAllClients(ArrayUtils.addAll(new String[]{"CMD", "", "LIST"}, clientlist));

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

                        for (ConnectedClient c : clients) {
                            System.out.print(c.getNickname() + " ");
                        }
                        System.out.println();
                        if (clients.size() == 0) System.out.println("nobody");
                        break;
                    case "addmod":
                        if (cmds.length <= 1) {
                            System.out.println("Whom do you want to add as a mod?");
                        } else {
                            String name = cmds[1];
                            boolean modded = false;
                            for (ConnectedClient c : clients) {
                                if (c.getNickname().equals(name)) {
                                    if (c.isModerator()) {
                                        System.out.printf("%s is already a mod!\n", name);
                                        modded = true;
                                        break;
                                    } else {
                                        modded = true;
                                        System.out.printf("%s is now a mod!\n", name);
                                        c.setModerator(true);
                                        moduuids.add(c.getUniqueId());
                                        sendToClient(c.getSocket(), new String[]{"CMD", "", "MODC"});
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

                            for (ConnectedClient c : clients) {
                                if (c.getNickname().equals(name)) {
                                    if (!c.isModerator()) {
                                        System.out.printf("%s is not a mod!\n", name);
                                        userfound = true;
                                    } else {
                                        moduuids.remove(c.getUniqueId());
                                        c.setModerator(false);
                                        sendToClient(c.getSocket(), new String[]{"CMD", "", "MODR"});
                                        System.out.printf("%s is no longer a mod!\n", name);
                                        userfound = true;
                                    }
                                }
                            }
                            //TODO: remove mods when they're not online
                            if (!userfound) System.out.printf("User %s not found!\n", name);
                        }
                        break;
                    case "quit":
                    case "stop":
                        //Save current settings / options and write them to the config
                        cfg.setMaxclients(maxclients);
                        cfg.setMotd(motd);
                        cfg.setBannedclients(bannedclients);
                        cfg.setModuuids(moduuids);

                        String newjson = gson.toJson(cfg);

                        config.delete();

                        try {
                            PrintWriter pw = new PrintWriter(config);
                            pw.write(newjson);
                            pw.close();
                            lockfile.delete();
                        } catch (IOException e) {
                            log("Error while saving config!.");
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
                                System.out.println("This is not a number.");
                                break;
                            }
                            if (newcount < 1) {
                                System.out.println("Error: This number is too low.");
                                break;
                            }
                            if (clientcount > newcount) {
                                System.out.println("Warning: The max player count is over the limit.");
                            }
                            maxclients = newcount;
                            System.out.printf("Successfully changed the client limit to %d!\n", newcount);
                        }
                        break;
                    case "getclientlimit":
                        System.out.printf("The client limit is set to %d clients\n", maxclients);
                        break;
                    case "ismod":
                        if (cmds.length <= 1) {
                            System.out.println("Whom do you want to check?");
                        } else {
                            if (isConnected(cmds[1])) {
                                if (isMod(getClient(cmds[1]).getUniqueId()))
                                    System.out.printf("%s is a mod\n", cmds[1]);
                                else System.out.printf("%s is not a mod\n", cmds[1]);
                            } else {
                                System.out.printf("%s is not online!\n", cmds[1]);
                            }
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
                    case "help":
                        System.out.println("currently available commands:");
                        System.out.println("online");
                        System.out.println("kick <nickname>");
                        System.out.println("ban <nickname>");
                        System.out.println("unban <nickname>");
                        System.out.println("addmod <nickname>");
                        System.out.println("removemod <nickname>");
                        System.out.println("ismod <nickname>");
                        System.out.println("isbanned <nickname>");
                        System.out.println("quit / stop");
                        System.out.println("setmotd <newmotd>");
                        System.out.println("setclientlimit <newlimit>");
                        System.out.println("getclientlimit");
                        System.out.println("help");
                        break;
                    default:
                        System.out.println("Command not found.");
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
        for (ConnectedClient c : clients) {
            if (c.getNickname().equals(client)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a client is connected
     *
     * @param client the client to check
     * @return true if client is connected otherwise false
     */
    private boolean isConnected(Socket client) {
        for (ConnectedClient c : clients) {
            if (c.getSocket().equals(client)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMod(UUID uuid) {
        if (moduuids.contains(uuid)) {
            return true;
        }
        return false;
    }

    private boolean isMod(Socket s) {
        for (ConnectedClient c : clients) {
            if (c.getSocket().equals(s)) {
                return true;
            }
        }

        return false;
    }

    protected static String[] getClientNames() {
        String[] temp = new String[clientcount];

        int i = 0;
        for (ConnectedClient c : clients) {
            temp[i] = c.getNickname();
            i++;
        }

        return temp;
    }


    /**
     * Search for an online client
     *
     * @param name The name of the online client
     * @return socket of the client if client is online otherwise null
     */
    private Socket getSocket(String name) {
        for (ConnectedClient c : clients) {
            if (c.getNickname().equals(name)) {
                return c.getSocket();
            }
        }
        return null;
    }

    private ConnectedClient getClient(String name) {
        for (ConnectedClient c : clients) {
            if (c.getNickname().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private ConnectedClient getClient(Socket client) {
        for (ConnectedClient c : clients) {
            if (c.getSocket().equals(client)) {
                return c;
            }
        }
        return null;
    }

    private boolean isTaken(String nickname) {
        for (ConnectedClient c : clients) {
            if (c.getNickname().equals(nickname)) {
                return true;
            }
        }
        return false;
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
                for (ConnectedClient c : clients) {
                    if (c.getNickname().equals(name)) {
                        sendToClient(c.getSocket(), new String[]{"CMD", "", "BAN"});
                        try {
                            c.getSocket().close();
                            if (log) System.out.printf("Banned client %s\n", name);
                            bannedclients.add(name);
                            return true;
                        } catch (IOException ignored) {
                            return false;
                        } finally {
                            clientcount--;
                            clients.remove(c);
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
        for (ConnectedClient c : clients) {
            if (c.getNickname().equals(name)) {
                kicked = true;
                sendToClient(c.getSocket(), new String[]{"CMD", "", "KICK"});
                try {
                    c.getSocket().close();
                    if (log) System.out.printf("Kicked client %s\n", name);
                    return true;
                } catch (IOException ignored) {

                } finally {
                    clientcount--;
                    clients.remove(c);
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

class TimeoutThread implements Runnable {

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            for (ConnectedClient c : Server.clients) {
                ObjectOutputStream out;
                try {
                    out = new ObjectOutputStream(c.getSocket().getOutputStream());
                    out.writeObject(new String[]{"CMD", "","CONTEST"});
                    out.flush();
                }
                catch (IOException e) {
                    Server.clientcount--;
                    Server.clients.remove(c);

                    System.out.printf("[LOG] Client %s (%s) has timed out\n", c.getNickname(), c.getRemoteAddress());

                    for (ConnectedClient cl : Server.clients) {
                        ObjectOutputStream outn;
                        //Send new clientlist to all players
                        String[] clientlist = Arrays.copyOf(Server.getClientNames(), Server.getClientNames().length, String[].class);
                        try {
                            outn = new ObjectOutputStream(cl.getSocket().getOutputStream());
                            outn.writeObject(new String[]{"MSG", "", cl.getNickname() + " left the chat room"});
                            outn.flush();
                            outn.writeObject(ArrayUtils.addAll(new String[]{"CMD", "", "LIST"}, clientlist));
                        }
                        catch (IOException ef) {
                            ef.printStackTrace();
                        }
                    }

                }
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
