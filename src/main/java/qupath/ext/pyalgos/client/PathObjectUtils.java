package qupath.ext.pyalgos.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


public class PathObjectUtils {
    private static final Logger logger = LoggerFactory.getLogger(PyAlgosClient.class);

    /**
     * Parse path objects from a JSON string.
     * Use this rather than the 'usual' JSON deserialization so that the object properties can also be extracted
     * From qupath-extension-sam's parsePathObjects (https://github.com/ksugar/qupath-extension-sam/blob/20bcdbdac26014006e839f8ee295d4438d325e20/src/main/java/org/elephant/sam/Utils.java#L56)
     *
     * @param json
     * @return a list of PathObjects, or empty list if none can be parsed
     */
    public static List<PathObject> parsePathObjects(String json) {
        Gson gson = GsonTools.getInstance();
        JsonElement element = gson.fromJson(json, JsonElement.class);
        if (element.isJsonObject() && element.getAsJsonObject().has("features"))
            // Handle the case where the response is a GeoJSON FeatureCollection
            element = element.getAsJsonObject().get("features");
        if (element.isJsonArray()) {
            // Handle an array of GeoJSON Features
            return element.getAsJsonArray().asList().stream()
                    .map(e -> parsePathObject(gson, e))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else if (element.isJsonObject()) {
            // Handle a single GeoJSON Feature
            PathObject pathObject = parsePathObject(gson, element);
            if (pathObject != null)
                return Collections.singletonList(pathObject);
        }
        logger.debug("Unable to parse PathObject from {}", json);
        return Collections.emptyList();
    }

    /**
     * Parse a single PathObject from a JsonElement.
     * Adapted from qupath-extension-sam's parsePathObjects
     * (https://github.com/ksugar/qupath-extension-sam/blob/20bcdbdac26014006e839f8ee295d4438d325e20/src/main/java/org/elephant/sam/Utils.java#L85)
     *
     * @param gson
     * @param element
     * @return the PathObject, or null if it cannot be parsed
     */
    private static PathObject parsePathObject(Gson gson, JsonElement element) {
        if (!element.isJsonObject()) {
            logger.warn("Cannot parse PathObject from {}", element);
            return null;
        }
        // Get the geojson.Feature as a PathObject
        JsonObject jsonObj = element.getAsJsonObject();
        PathObject pathObject = gson.fromJson(jsonObj, PathObject.class);
        // Add the Feature's properties as PathObject's measurements (if the value can be type-casted to a Number)
        if (jsonObj.has("properties")) {
            JsonObject properties = jsonObj.get("properties").getAsJsonObject();
            for (String key : properties.keySet()) {
                if (key.equals("Classification")) {
                    pathObject.setPathClass(PathClass.getInstance(properties.get(key).getAsString()));
                } else {
                    try {
                        Number meas = properties.get(key).getAsNumber();
                        pathObject.getMeasurements().put(key, meas);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return pathObject;
    }
}
