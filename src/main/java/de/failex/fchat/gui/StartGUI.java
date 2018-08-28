package de.failex.fchat.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class StartGUI extends Application {


    public void runGUI(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        //Setup GUI and start controller
        FXMLLoader loader = new FXMLLoader();
        Parent root = loader.load(getClass().getResourceAsStream("/maingui.fxml"));
        primaryStage.setTitle("FChat");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        new MainGUI(loader.getController(), primaryStage);
        primaryStage.show();
    }
}
