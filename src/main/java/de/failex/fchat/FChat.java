package de.failex.fchat;

import de.failex.fchat.GUI.MainGUI;
import de.failex.fchat.GUI.MainGUIController;
import de.failex.fchat.Server.Server;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FChat extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        //Setup GUI and start controller
        FXMLLoader loader = new FXMLLoader();
        Parent root = loader.load(getClass().getResourceAsStream("/maingui.fxml"));
        primaryStage.setTitle("FChat");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        new MainGUI((MainGUIController) loader.getController(), primaryStage);
        primaryStage.show();
    }


    public static void main(String[] args) {
        //Start the server when the program starts with an argument
        if (args.length == 0) {
            launch(args);
            System.exit(0);
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
