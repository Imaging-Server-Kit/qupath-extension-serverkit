package qupath.ext.serverkit.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.serverkit.client.Client;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.plugins.parameters.ParameterList;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Map;

public class ServerKitUI {

    private final static Logger logger = LoggerFactory.getLogger(ServerKitUI.class);

    private final QuPathGUI qupath;

    private final String extName;

    private final String extMenuName;

    private TextField URLtextField;

    public ServerKitUI(QuPathGUI qupath, String name, String extMenuName) {
        super();
        this.qupath = qupath;
        this.extName = name;
        this.extMenuName = extMenuName;
    }

    /**
     * Build the GridPane of the window for connecting to the server
     * @return
     */
    private GridPane buildConnectionGridPane() {
        GridPane gp = new GridPane();
        gp.setHgap(5.0);
        gp.setVgap(5.0);
        Label URLLabel = new Label("Enter the algorithm server URL");
        GridPaneUtils.addGridRow(gp, 0, 0, null, URLLabel);
        URLtextField = new TextField(Client.defaultUrl);
        GridPaneUtils.addGridRow(gp, 1, 0, "By default: " + Client.defaultUrl, URLtextField);
        return gp;
    }

    /**
     * Add a menu item for connecting to the server
     */
    public void addConnectionMenuItem() {
        Menu algosMenu = qupath.getMenu(extMenuName, true);
        MenuItem connectionMenuItem = new MenuItem("Connect...");
        MenuTools.addMenuItems(algosMenu, connectionMenuItem);
        this.setOnConnect(connectionMenuItem);
    }

    /**
     * Define the action linked to the firing of the "Connect..." menu item
     *
     * @param mi
     */
    private void setOnConnect(MenuItem mi) {
        mi.setOnAction(e -> {
            // Display a dialog window to connect to the server
            GridPane gp = buildConnectionGridPane();
            boolean confirm = Dialogs.showConfirmDialog("Server URL", gp);
            if (!confirm) return;
            String serverURL = URLtextField.getText();
            if (serverURL == null || serverURL.isEmpty()) return;

            // Get the Client instance & connect to the input server URL
            Client client = Client.getInstance();
            try {
                client.launchHttpClient(serverURL);
                String successMessage = "Successfully connected to server on " + client.getServerURL().toString();
                logger.info(successMessage);
                Dialogs.showInfoNotification(extName, successMessage);
            } catch (MalformedURLException urlException) {
                Dialogs.showErrorMessage("Algorithm server", "Invalid URL: " + serverURL);
                clearAlgos();
                return;
            } catch (IOException ioException) {
                String ioErrMessage = "Could not connect to server on " + client.getServerURL().toString();
                logger.error(ioErrMessage);
                Dialogs.showErrorNotification(extName, ioErrMessage);
                clearAlgos();
                return;
            }

            // Get the available algorithms from the server and add them as menu items
            try {
                this.addAvailableAlgos();
            } catch (IOException | InterruptedException algoExc) {
                String errMessage = "Could not retrieve algorithms from the server";
                logger.error(errMessage);
                Dialogs.showErrorNotification(extName, errMessage);
            }
        });
    }

    /**
     * Remove the existing algorithms from the extension sub-menu
     */
    private void clearAlgos() {
        Menu algosMenu = qupath.getMenu(extMenuName, false);
        algosMenu.getItems().clear();
        addConnectionMenuItem();
    }

    /**
     * Query the available algorithms from the server & add them as menu items
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private void addAvailableAlgos() throws IOException, InterruptedException {
        clearAlgos();
        Client client = Client.getInstance();
        if (!client.isConnected()) {
            String ioErrMessage = "Could not connect to server on " + client.getServerURL().toString();
            logger.error(ioErrMessage);
            Dialogs.showErrorNotification(extName, ioErrMessage);
            return;
        }
        String[] availableAlgorithms = client.getAlgos();
        if (availableAlgorithms.length == 0) {
            logger.warn("No algorithms available on the server");
        } else {
            for (String algoName : availableAlgorithms) {
                MenuItem menuitem = new MenuItem(algoName);
                Menu algosMenu = qupath.getMenu(extMenuName, false);
                MenuTools.addMenuItems(algosMenu, menuitem);
                this.setOnAlgo(menuitem, algoName);
            }
            logger.info("Added the available algorithms " + Arrays.toString(availableAlgorithms) + " to the " + extMenuName + " menu");
        }
    }

    /**
     * Define the action linked to the firing of the algorithm menu item designated by algoName
     *
     * @param mi
     * @param algoName
     */
    public void setOnAlgo(MenuItem mi, String algoName) {
        mi.setOnAction(ae -> {
            Client client = Client.getInstance();
            if (!client.isConnected()) {
                logger.error("Could not connect to server on {}", client.getServerURL());
                return;
            }
            try {
                // Get algorithm parameters from the given algoName
                JsonObject parametersJson = client.getParameters(algoName);

                if (parametersJson == null || parametersJson.isEmpty()) {
                    // Run the algo directly
                    logger.info("Running {}...", algoName);
                    try {
                        client.run(qupath, qupath.getViewer(), algoName, null);
                    } catch (Exception e) {
                        logger.error(e.getLocalizedMessage());
                    }
                } else {
                    // Create a ParameterList
                    ParameterList parameterList = new ParameterList();

                    // Fill it in
                    for (Map.Entry<String, JsonElement> entry : parametersJson.entrySet()) {
                        String key = entry.getKey();
                        JsonObject parameterValues = entry.getValue().getAsJsonObject();
                        String prompt = parameterValues.get("title").getAsString();
                        String description = parameterValues.get("description") != null ? parameterValues.get("description").getAsString() : null;
                        JsonElement defaultValue = parameterValues.get("default") != null ? parameterValues.get("default") : null;

                        switch (parameterValues.get("widget_type").getAsString()) {
                            case "bool":
                                parameterList.addBooleanParameter(key, prompt, defaultValue.getAsBoolean(), description);
                                break;
                            case "int":
                                parameterList.addIntParameter(key, prompt, defaultValue.getAsInt(), null, description);
                                break;
                            case "float":
                                parameterList.addDoubleParameter(key, prompt, defaultValue.getAsDouble(), null, description);
                                break;
                            case "str":
                                parameterList.addStringParameter(key, prompt, defaultValue.getAsString(), description);
                                break;
                            case "dropdown":
                                JsonArray choicesArray = parameterValues.get("enum").getAsJsonArray();
                                String[] choices = new String[choicesArray.size()];
                                for (int i = 0; i < choicesArray.size(); i++) {
                                    choices[i] = choicesArray.get(i).getAsString();
                                }
                                parameterList.addChoiceParameter(key, prompt, choices[0], Arrays.stream(choices).toList(), description);
                                break;
                        }
                    }

                    // Create a ParametersDialog
                    ParametersDialog parametersDialog = new ParametersDialog(qupath, algoName, parameterList);
                }
            } catch (Exception exception) {
                logger.error(exception.getLocalizedMessage());
            }
        });
    }
}
