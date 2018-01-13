package de.failex.fchat;

import de.failex.fchat.GUI.MainGUI;
import de.failex.fchat.Server.Server;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FChat extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        Parent root = loader.load(getClass().getResourceAsStream("maingui.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        new MainGUI(loader.getController(), primaryStage);
        primaryStage.show();
    }


    public static void main(String[] args) {
        if (args.length == 0) launch(args);
        try {
            int port = Integer.parseInt(args[0]);
            new Server(port);
        }
        catch (NumberFormatException e) {
            System.err.println("That is not a valid port number!");
            System.exit(1);
        }
        System.exit(0);
    }
}
