package de.failex.fchat;

import de.failex.fchat.gui.StartGUI;
import de.failex.fchat.server.Server;

public class FChat {


    public static void main(String[] args) {
        //Start the server when the program starts with an argument
        if (args.length == 0) {
            (new StartGUI()).runGUI(args);
        } else {
            try {
                int port = Integer.parseInt(args[0]);
                new Server(port);
            } catch (NumberFormatException e) {
                System.err.println("invalid port number!");
                System.exit(1);
            }
        }
    }
}
