package qupath.ext.pyalgos.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.pyalgos.PyAlgosExtension;
import qupath.ext.pyalgos.client.PyAlgosClient;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.plugins.parameters.ParameterList;

import java.io.IOException;

public class ParametersDialog extends ParameterPanelFX {
    private final static Logger logger = LoggerFactory.getLogger(PyAlgosExtension.class);
    private final Button btnRun = new Button("Run");

    private final QuPathGUI qupath;

    private final String algoName;

    private final ParameterList parameterList;

    public ParametersDialog(QuPathGUI qupath, String algoName, ParameterList parameterList) {
        super(parameterList);
        this.qupath = qupath;
        this.algoName = algoName;
        this.parameterList = parameterList;

        Stage dialog = buildUI();
        if (qupath != null) dialog.initOwner(qupath.getStage());
        dialog.setTitle("Parameters");
        btnRun.requestFocus();
        dialog.show();
    }

    public Stage buildUI() {
        // Set padding
        this.getPane().setPadding(new Insets(5, 5, 5, 5));

        // Add the parameters to the content of the ScrollPane
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(this.getPane());
        scrollPane.setFitToWidth(true);

        // Add the scrollPane to the center of the BorderPane
        BorderPane pane = new BorderPane();
        pane.setCenter(scrollPane);

        // Bind the enabling/disabling of the Run button with its label
        btnRun.textProperty().bind(Bindings.createStringBinding(() -> {
            if (btnRun.isDisabled()) return "Please wait...";
            else return "Run";
        }, btnRun.disabledProperty()));
        // Define its action
        btnRun.setOnAction(e -> handle(e));

        // Add it to the bottom of the BorderPane
        btnRun.setMaxWidth(Double.MAX_VALUE);
        btnRun.setPadding(new Insets(5, 5, 5, 5));
        pane.setBottom(btnRun);

        // Set the max size for all panels/panes
        this.getPane().setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        scrollPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Create a Scene for the main pane, create a Stage and set the Stage's scene
        Scene scene = new Scene(pane);
        Stage dialogStage = new Stage();
        dialogStage.setMinWidth(300);
        dialogStage.setMinHeight(120);
        dialogStage.setScene(scene);

        return dialogStage;
    }

    public void handle(ActionEvent event) {
        if (event.getSource() == btnRun) {
            btnRun.setDisable(true);
            new Thread(() -> {
                try {
                    logger.info("Running {}...", algoName);
                    PyAlgosClient client = PyAlgosClient.getInstance();
                    if (!client.isConnected()) {
                        throw new IOException();
                    }
                    client.run(qupath, qupath.getViewer(), algoName, parameterList);  // Shouldn't this be userParameterList?
                } catch (Exception e) {
                    logger.error(e.getLocalizedMessage());
                }
                Platform.runLater(() -> {
                    btnRun.setDisable(false);
                });
            }).start();
        }
    }
}
