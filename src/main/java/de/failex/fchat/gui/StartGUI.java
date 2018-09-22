package de.failex.fchat.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class StartGUI extends Application {

    boolean forcelaunch;
    MainGUI program;
    File lockfile = new File(("data" + File.separator + "fchat.lock").replace(" ", ""));

    public void runGUI(String[] args, boolean forcelaunch) {
        this.forcelaunch = forcelaunch;
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
        program = new MainGUI(loader.getController(), primaryStage, forcelaunch);
        primaryStage.show();
    }

    @Override
    public void stop() {
        MainGUI.disconnect();
        lockfile.delete();

    }
}
