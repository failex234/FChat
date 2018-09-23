package de.failex.fchat;

import de.failex.fchat.gui.StartGUI;
import de.failex.fchat.server.Server;

public class FChat {


    public static void main(String[] args) {
        //Start the server when the program starts with an argument
        if (args.length == 0) {
            (new StartGUI()).runGUI(args, false);
        } else {
            try {
                int port = Integer.parseInt(args[0]);
                if (args.length > 1) {
                    new Server(port, args[1].equals("--force"));
                } else {
                    new Server(port, false);
                }
            } catch (NumberFormatException e) {
                if (args[0].equals("--force")) {
                    (new StartGUI()).runGUI(args, true);
                } else {
                    System.err.println("invalid port number!");
                    System.exit(1);
                }
            }
        }
    }
}
