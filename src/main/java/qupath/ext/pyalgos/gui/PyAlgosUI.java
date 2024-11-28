package qupath.ext.pyalgos.gui;

import com.google.gson.JsonObject;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.pyalgos.PyAlgosExtension;
import qupath.ext.pyalgos.client.ParametersUtils;
import qupath.ext.pyalgos.client.PyAlgosClient;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.plugins.parameters.ParameterList;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

public class PyAlgosUI {

    private final static Logger logger = LoggerFactory.getLogger(PyAlgosExtension.class);

    private final QuPathGUI qupath;

    private final String extName;

    private final String extMenuName;

    private TextField URLtextField;

    public PyAlgosUI(QuPathGUI qupath, String name, String extMenuName) {
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
        Label URLLabel = new Label("Enter the Python algos server URL");
        GridPaneUtils.addGridRow(gp, 0, 0, null, URLLabel);
        URLtextField = new TextField(PyAlgosClient.defaultUrl);
        GridPaneUtils.addGridRow(gp, 1, 0, "By default: " + PyAlgosClient.defaultUrl, URLtextField);
        return gp;
    }

    /**
     * Add a menu item for the connection for the server
     */
    public void addConnectionMenuItem() {
        Menu pyalgosMenu = qupath.getMenu(extMenuName, true);
        MenuItem connectionMenuItem = new MenuItem("Connect...");
        MenuTools.addMenuItems(pyalgosMenu, connectionMenuItem);
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
            boolean confirm = Dialogs.showConfirmDialog("Python algos server URL", gp);
            if (!confirm) return;
            String serverURL = URLtextField.getText();
            if (serverURL == null || serverURL.isEmpty()) return;

            // Get the PyAlgoClient instance & connect to the input server URL
            PyAlgosClient client = PyAlgosClient.getInstance();
            try {
                client.launchHttpClient(serverURL);
                String successMessage = "Client successfully connected to server on " + client.getServerURL().toString();
                logger.info(successMessage);
                Dialogs.showInfoNotification(extName, successMessage);
            } catch (MalformedURLException urlException) {
                Dialogs.showErrorMessage("Python algos server", "Invalid URL: " + serverURL);
                clearAlgos();
                return;
            } catch (IOException ioException) {
                String ioErrMessage = "Client could not connect to server on " + client.getServerURL().toString();
                logger.error(ioErrMessage);
                Dialogs.showErrorNotification(extName, ioErrMessage);
                clearAlgos();
                return;
            }

            // Get the available algos from the server and add them as menu items
            try {
                this.addAvailableAlgos();
            } catch (IOException | InterruptedException algoExc) {
                String errMessage = "Client could not retrieve the available algos from the server";
                logger.error(errMessage);
                Dialogs.showErrorNotification(extName, errMessage);
            }
        });
    }

    /**
     * Remove the existing algorithms from the extension sub-menu
     */
    private void clearAlgos() {
        Menu pyalgosMenu = qupath.getMenu(extMenuName, false);
        pyalgosMenu.getItems().clear();
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
        // Add the algos
        PyAlgosClient client = PyAlgosClient.getInstance();
        if (!client.isConnected()) {
            String ioErrMessage = "Client could not connect to server on " + client.getServerURL().toString();
            logger.error(ioErrMessage);
            Dialogs.showErrorNotification(extName, ioErrMessage);
            return;
        }
        String[] algos = client.getAlgos();
        if (algos.length == 0) {
            logger.warn("No algorithms available on the server");
        } else {
            for (String algoName : algos) {
                MenuItem menuitem = new MenuItem(algoName);
                Menu pyalgosMenu = qupath.getMenu(extMenuName, false);
                MenuTools.addMenuItems(pyalgosMenu, menuitem);
                this.setOnAlgo(menuitem, algoName);
            }
            logger.info("Added the available algorithms " + Arrays.toString(algos) + " to the " + extMenuName + " menu");
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
            PyAlgosClient client = PyAlgosClient.getInstance();
            if (!client.isConnected()) {
                logger.error("Client could not connect to server on {}", client.getServerURL());
                return;
            }
            try {
                // Get the list of required parameter for the given algoName (via a request to the server)
                JsonObject parametersJson = client.getRequiredParameters(algoName);
//                List<JsonObject> parametersJson = client.getRequiredParameters(algoName);

                ParameterList parameterList = ParametersUtils.createParameterList(parametersJson, algoName);

                // If there are parameters, display them in a dialog - if not, run the algorithm directly
                if (!parameterList.getKeyValueParameters(false).isEmpty()) {
                    // Display the required parameters, when clicking on "Run", read the user-defined values and run the algo
                    ParametersDialog parametersDialog =
                            new ParametersDialog(qupath, algoName, parameterList);
                } else {
                    logger.info("Running {}...", algoName);
                    try {
                        client.runOneShot(qupath, qupath.getViewer(), algoName, null);
                    } catch (Exception e) {
                        logger.error(e.getLocalizedMessage());
                    }
                }
            } catch (Exception exception) {
                logger.error(exception.getLocalizedMessage());
            }
        });
    }
}
