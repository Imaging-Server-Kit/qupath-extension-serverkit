package qupath.ext.serverkit;

import qupath.ext.pyalgos.gui.ServerKitUI;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

public class ServerkitExtension implements QuPathExtension {

    static private String name = "Imaging Server Kit";

    static final private String extMenuName = "Extensions>" + name;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return "A QuPath extension to run Python-based image processing algorithms";
    }

    public void installExtension(QuPathGUI qupath) {
        ServerKitUI serverKitUI = new ServerKitUI(qupath, name, extMenuName);
        serverKitUI.addConnectionMenuItem();
    }
}
