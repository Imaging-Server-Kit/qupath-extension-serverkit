package qupath.ext.pyalgos.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.PathObject;


public class PathObjectUtils {
    private static final Logger logger = LoggerFactory.getLogger(PyAlgosClient.class);

    /**
     * Parse a single PathObject from a JsonElement.
     * Adapted from qupath-extension-sam's parsePathObjects
     * (https://github.com/ksugar/qupath-extension-sam/blob/20bcdbdac26014006e839f8ee295d4438d325e20/src/main/java/org/elephant/sam/Utils.java#L85)
     *
     * @param gson
     * @param element
     * @return the PathObject, or null if it cannot be parsed
     */
    public static PathObject parsePathObject(Gson gson, JsonElement element) {
        if (!element.isJsonObject()) {
            logger.warn("Cannot parse PathObject from {}", element);
            return null;
        }
        JsonObject jsonObj = element.getAsJsonObject();
        return gson.fromJson(jsonObj, PathObject.class);
    }
}
