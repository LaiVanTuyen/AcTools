package com.vti.tuyn.actools;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

public class AcToolsApplication extends Application {
    @Override
    public void start(Stage stage) {
        try {
            System.out.println("Loading FXML...");
            FXMLLoader fxmlLoader = new FXMLLoader(AcToolsApplication.class.getResource("hello-view.fxml"));
            System.out.println("Creating scene...");
            Scene scene = new Scene(fxmlLoader.load(), 800, 600);
            stage.setTitle("URL Automation Tool");
            stage.setScene(scene);
            System.out.println("Showing stage...");
            stage.show();
        } catch (IOException e) {
            System.err.println("Error loading FXML: " + e.getMessage());
            e.printStackTrace();
            showError("Application Error", "Failed to start application", e.getMessage());
            Platform.exit();
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            showError("Application Error", "An unexpected error occurred", e.getMessage());
            Platform.exit();
        }
    }

    private void showError(String title, String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public static void main(String[] args) {
        try {
            System.out.println("Starting application...");
            launch(args);
        } catch (Exception e) {
            System.err.println("Failed to launch application: " + e.getMessage());
            e.printStackTrace();
        }
    }
}