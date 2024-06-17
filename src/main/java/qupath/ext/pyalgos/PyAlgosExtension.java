package qupath.ext.pyalgos;

import qupath.ext.pyalgos.gui.PyAlgosUI;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;


public class PyAlgosExtension implements QuPathExtension {

    static private String name = "Python algos";

    static final private String extMenuName = "Extensions>" + name;


    public String getName() {
        return "Python algos";
    }

    public String getDescription() {
        return "A QuPath extension to run Python-based image processing algorithms";
    }


    public void installExtension(QuPathGUI qupath) {
        PyAlgosUI pyAlgosUI = new PyAlgosUI(qupath, name, extMenuName);
        pyAlgosUI.addConnectionMenuItem();
    }
}
