package qupath.ext.pyalgos.client;

import com.google.gson.*;
import ij.ImagePlus;
import ij.io.FileSaver;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class PyAlgosClient {
    // Logger
    private final static Logger logger = LoggerFactory.getLogger(PyAlgosClient.class);

    // Python API URL defined by the hostname or IP address and port of the server
    private URL apiUrl;

    // Default API URL
    public static String defaultUrl = "http://127.0.0.1:7000";

    // The client handling HTTP requests
    private final PyAlgosHttpClient httpClient = new PyAlgosHttpClient();

    private static PyAlgosClient instance = new PyAlgosClient();

    private PyAlgosClient() {
    }

    public static PyAlgosClient getInstance() {
        return instance;
    }

    public void launchHttpClient(String URL) throws IOException {
        URL = URL.trim();
        if (URL.endsWith("/")) {
            URL = URL.substring(0, URL.length() - 1);
        }
        this.apiUrl = new URL(URL);
        httpClient.setURL(this.apiUrl);
        if (!httpClient.isConnected()) throw new IOException();
    }

    public URL getServerURL() {
        return apiUrl;
    }

    /**
     * Check if the Http Client exists and can successfully connect to the server
     *
     * @return
     */
    public boolean isConnected() {
        return this.httpClient.isConnected();
    }

    /**
     * Parse the {@link HttpResponse}'s body as a {@link JsonObject}
     *
     * @param response
     * @return
     * @throws IOException
     */
    public static JsonObject parseResponseToJsonObject(HttpResponse<String> response) throws IOException {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /**
     * Parse the {@link HttpResponse}'s body as {@link JsonObject}, then parse the list of {@link JsonObject} from the
     * value of the JsonObject at the given key
     *
     * @param response
     * @param key
     * @return
     * @throws IOException
     */
    public static JsonObject parseResponseToJsonObjectList(HttpResponse<String> response) throws IOException {
//    public static List<JsonObject> parseResponseToJsonObjectList(HttpResponse<String> response) throws IOException {
        JsonObject object = parseResponseToJsonObject(response);

        JsonObject properties = object.get("properties").getAsJsonObject();

        // Return properties here?
        return properties;

        // This is also returned by the schema (a list of required parameters):
//        JsonArray requiredParams = object.get("required").getAsJsonArray();

//        // Convert properties JsonObject to a List<JsonObject>
//        List<JsonObject> list = new ArrayList<>();
//        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
//            JsonObject jsonValue = entry.getValue().getAsJsonObject();
//            list.add(jsonValue);
//        }
//
////        JsonArray array = object.get(key).getAsJsonArray();
////        List<JsonObject> list = new ArrayList<>(array.size());
////        for (JsonElement element : array) {
////            list.add(element.getAsJsonObject());
////        }
//        return list;
    }

    /**
     * Parse a {@link HttpResponse} into a list of {@link PathObject}
     * Adapted from qupath-extension-sam's parseResponse (https://github.com/ksugar/qupath-extension-sam/blob/20bcdbdac26014006e839f8ee295d4438d325e20/src/main/java/org/elephant/sam/tasks/SAMDetectionTask.java#L217)
     * & applyTransformAndClassification (https://github.com/ksugar/qupath-extension-sam/blob/20bcdbdac26014006e839f8ee295d4438d325e20/src/main/java/org/elephant/sam/Utils.java#L150)
     *
     * @param response
     * @param regionRequest
     * @return
     */
    private List<PathObject> parseResponseToPathObjectList(HttpResponse<String> response, RegionRequest regionRequest) {
        // Get the pathObjects (with associated measurements & classification) from the list of features
        // in the response's body
        List<PathObject> pathObjects = PathObjectUtils.parsePathObjects(response.body());

        // Place the pathObjects correctly even if the viewer changed
        AffineTransform transform = new AffineTransform();
        transform.translate(regionRequest.getMinX(), regionRequest.getMinY());
        transform.scale(regionRequest.getDownsample(), regionRequest.getDownsample());
        ImagePlane plane = regionRequest.getImagePlane();

        List<PathObject> updatedObjects = new ArrayList<>();
        for (PathObject pathObject : pathObjects) {
            if (!transform.isIdentity()) {
                pathObject = PathObjectTools.transformObject(pathObject, transform, true);
            }
            if (plane != null && !Objects.equals(plane, pathObject.getROI().getImagePlane()))
                pathObject = PathObjectTools.updatePlane(pathObject, plane, true, false);
            updatedObjects.add(pathObject);
        }
        return updatedObjects;
    }

    public String[] getAlgos() throws IOException, InterruptedException {
        JsonObject algos = parseResponseToJsonObject(this.httpClient.getAlgosNames());

        JsonArray algosJsonArray = algos.get("services").getAsJsonArray();
//        JsonArray algosJsonArray = algos.get("algos_names").getAsJsonArray();

        String[] arr = new String[algosJsonArray.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = algosJsonArray.get(i).getAsString();
        }

        return arr;
    }

    /**
     * Send the image as a byte array via HTTP POST request
     *
     * @param image {{@link ImagePlus}
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> sendImage(ImagePlus image) throws ExecutionException, InterruptedException {
        byte[] serializedImage = new FileSaver(image).serialize();
        return this.httpClient.sendImage(serializedImage);
    }


    /**
     * Send the image as a JsonObject via HTTP POST request
     *
     * @param image {{@link ImagePlus}
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> sendImageJson(ImagePlus image) throws ExecutionException, InterruptedException {
        byte[] serializedImage = new FileSaver(image).serialize();
        String img = Base64.getEncoder().encodeToString(serializedImage);
        JsonObject imageJson = new JsonObject();
        imageJson.addProperty("data", img);
        return this.httpClient.sendImage(imageJson);
    }

    public JsonObject getRequiredParameters(String algoName) throws IOException, InterruptedException {
//    public List<JsonObject> getRequiredParameters(String algoName) throws IOException, InterruptedException {
        HttpResponse<String> paramsResponse = this.httpClient.getAlgoRequiredParams(algoName);
        return parseResponseToJsonObjectList(paramsResponse);
    }

    /**
     * Set the algorithm's parameters via HTTP POST request
     *
     * @param algoName   Name of the algorithm
     * @param parameters Json-formatted string containing the parameters
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> setParameters(String algoName, String parameters)
            throws ExecutionException, InterruptedException {
        return this.httpClient.setAlgoParams(algoName, parameters);
    }

    /**
     * Set the algorithm's parameter via HTTP POST request
     *
     * @param algoName      Name of the algorithm
     * @param parameterList The algorithm parameters (name and value)
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> setParameters(String algoName, ParameterList parameterList)
            throws ExecutionException, InterruptedException {
        Map<String, Object> map = parameterList.getKeyValueParameters(false);
        Map<String, Object> parametersMap = new LinkedHashMap<>();
        parametersMap.put("parameters", map);
        String parameters = ParameterList.convertToJson(parametersMap);
        return setParameters(algoName, parameters);
    }

    /**
     * Send the POST request to compute the result for the given algoName
     *
     * @param algoName
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> computeResult(String algoName) throws ExecutionException, InterruptedException {
        return this.httpClient.computeResult(algoName);
    }


    /**
     * Get the computed result via HTTP GET request
     *
     * @param algoName
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public JsonObject getComputedResult(String algoName) throws IOException, InterruptedException {
        HttpResponse<String> response = this.httpClient.getComputedResult(algoName);
        return parseResponseToJsonObject(response);
    }

    /**
     * Get the geojson features from the computed result via HTTP GET request
     *
     * @param algoName
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getComputedResultFeature(String algoName) throws IOException, InterruptedException {
        return this.httpClient.getComputedResultFeatures(algoName);
    }

    /**
     * Get the specified endpoint result from the computed result via HTTP GET request
     *
     * @param algoName
     * @param endpoint
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getComputedResultEndpoint(String algoName, String endpoint)
            throws IOException, InterruptedException {
        return this.httpClient.getComputedResultEndpoint(algoName, endpoint);
    }

    /**
     * Log a message at the WARN level with the details of the HTTP response
     *
     * @param response
     * @param description
     */
    private void logHttpWarning(HttpResponse<String> response, String description) {
        String detail = null;
        try {
            detail = parseResponseToJsonObject(response).get("detail").getAsString();
        } catch (IOException ignored) {
        } finally {
            logger.warn("{} (HTTP HttpResponse {}: {})",
                    description, response.statusCode(), detail);
        }
    }

    /**
     * Log a message at the ERROR level with the details of the HTTP response
     *
     * @param response
     * @param description
     */
    private void logHttpError(HttpResponse<String> response, String description) {
        String detail = null;
        try {
            JsonElement jsonHttpResponse = parseResponseToJsonObject(response).get("detail");
            detail = (jsonHttpResponse == null) ? null : jsonHttpResponse.getAsString();
        } catch (IOException ignored) {
        }
        logger.error("{} (HTTP HttpResponse {}: {})",
                description, response.statusCode(), detail);
    }

    public void runOneShot(QuPathGUI qupath, QuPathViewer qupathViewer, String algoName, ParameterList parameterList)
            throws ExecutionException, IOException, InterruptedException {

        Map<String, Object> parametersMap = new LinkedHashMap<>();
        JsonObject parametersJson = new JsonObject();

        // Convert parameters to JSON
        if (parameterList != null) {
            Map<String, Object> map = parameterList.getKeyValueParameters(false);
            parametersMap.putAll(map);
//            String parameters = ParameterList.convertToJson(map);

//            Map<String, Object> parametersMap = new LinkedHashMap<>();
//            parametersMap.put("parameters", map);
//            String parameters = ParameterList.convertToJson(parametersMap);
        }

        // Get the image within the selected annotation as JSON with "data" property and b64-encoded
        PathObject selectedObject = getSelectedObject(qupathViewer);
        if (selectedObject == null) {
            Dialogs.showErrorMessage("Python algos error", "No annotation selected");
            return;
        }
        ImageServer<BufferedImage> imageServer = getImageServer(qupathViewer);
        RegionRequest viewerRegion = getRegionRequest(qupathViewer, imageServer, selectedObject);
        ImagePlus img = IJTools.convertToImagePlus(imageServer, viewerRegion).getImage();
        byte[] serializedImage = new FileSaver(img).serialize();
        String imgEncoded = Base64.getEncoder().encodeToString(serializedImage);

        parametersMap.put("image", imgEncoded);

        String parameters = ParameterList.convertToJson(parametersMap);

        // At this point, we should be ready to send the encoded image and parameters to the server
        // ...

//        JsonObject imageJson = new JsonObject();
//        imageJson.addProperty("data", imgEncoded);  // Could this be appended to the parametersJSON?
//        parametersJson.addProperty("data", imgEncoded);  // Could this be appended to the parametersJSON?

        // Run the algo
        HttpResponse<String> processingResponse = this.httpClient.computeOneShot(algoName, parameters);
        if (processingResponse.statusCode() != 201) {
            logHttpError(processingResponse, "Processing with " + algoName + " failed");
            return;
        }

        // Process the response body
        JsonArray dataTuplesList = JsonParser.parseString(processingResponse.body()).getAsJsonArray();  // This is a list of LayerDataTuple
        for (JsonElement element : dataTuplesList) {
            String resultType = element.getAsJsonObject().get("type").getAsString();  // Type of result, e.g. `image`, `labels`, `points`, etc.

//            JsonObject dataParams = element.getAsJsonObject().get("data_params").getAsJsonObject();  // Not used yet

            // Handle segmentation results returned as `features` (polygons)
            switch (resultType) {
                case "features":
                    // Decode the Polygon features
                    JsonArray encodedData = element.getAsJsonObject().get("data").getAsJsonArray();
                    Gson gson = GsonTools.getInstance();

                    List<PathObject> pathObjects = encodedData.asList().stream()
                            .map(e -> PathObjectUtils.parsePathObject(gson, e))
                            .filter(Objects::nonNull)
                            .toList();

//                    List<PathObject> detections = this.parseResponseToPathObjectList(encodedData, viewerRegion);

                    // Get the pathObjects (with associated measurements & classification) from the list of features
                    // in the response's body
//                    List<PathObject> pathObjects = PathObjectUtils.parsePathObjects(response.body());

                    // Place the pathObjects correctly even if the viewer changed
                    AffineTransform transform = new AffineTransform();
                    transform.translate(viewerRegion.getMinX(), viewerRegion.getMinY());
                    transform.scale(viewerRegion.getDownsample(), viewerRegion.getDownsample());
                    ImagePlane plane = viewerRegion.getImagePlane();

                    List<PathObject> detections = new ArrayList<>();
                    for (PathObject pathObject : pathObjects) {
                        if (!transform.isIdentity()) {
                            pathObject = PathObjectTools.transformObject(pathObject, transform, true);
                        }
                        if (plane != null && !Objects.equals(plane, pathObject.getROI().getImagePlane()))
                            pathObject = PathObjectTools.updatePlane(pathObject, plane, true, false);
                        detections.add(pathObject);
                    }
                    // Display the results
                    this.displayResult(qupath, selectedObject, detections);
                    break;
                // None of the rest are handled at the moment
                case "shapes":
                    break;
                case "image":
                    break;
                case "labels":
                    break;
                case "points":
                    break;
                case "tracks":
                    break;
                case "vectors":
                    break;
            }
        }

    }
    /**
     * Run the algorithm and display the result
     *
     * @param qupath
     * @param qupathViewer
     * @param algoName
     * @return
     * @throws Exception
     */
    public void run(QuPathGUI qupath, QuPathViewer qupathViewer, String algoName, ParameterList parameterList)
            throws ExecutionException, IOException, InterruptedException {
        // Send the parameters set by the user via a POST request (converts the parameters to json)
        if (parameterList != null) {
            HttpResponse<String> parametersResponse = setParameters(algoName, parameterList);
            if (parametersResponse.statusCode() != 201) {
                logHttpError(parametersResponse, "Could not set the user parameters");
                return;
            }
        }

        // Send the image selected by the user
        PathObject selectedObject = getSelectedObject(qupathViewer);
        if (selectedObject == null) {
            Dialogs.showErrorMessage("Python algos error", "No annotation selected");
            return;
        }
        ImageServer<BufferedImage> imageServer = getImageServer(qupathViewer);
        RegionRequest viewerRegion = getRegionRequest(qupathViewer, imageServer, selectedObject);
        ImagePlus img = IJTools.convertToImagePlus(imageServer, viewerRegion).getImage();

        HttpResponse<String> imgSentResponse = this.sendImage(img);
        if (imgSentResponse.statusCode() != 201) {
            logHttpError(imgSentResponse, "Could not send image to server");
            return;
        }

        // Run the algo
        HttpResponse<String> processingResponse = this.computeResult(algoName);
        if (processingResponse.statusCode() != 201) {
            // Processing failed, so there is no result to fetch
            logHttpError(processingResponse, "Processing with " + algoName + " failed");
            return;
        }

        // Check which endpoints are available for this algo to display the result accordingly
        List<String> endpoints = new ArrayList<>();
        try {
            JsonObject endpointsJson = parseResponseToJsonObject(processingResponse);
            JsonArray endpointsArray = endpointsJson.get("output_endpoints").getAsJsonArray();
            for (JsonElement element : endpointsArray) {
                endpoints.add(element.getAsString());
            }
        } catch (Exception e) {
            logger.error("Unknown output types, cannot display result");
            return;
        }
        if (endpoints.contains("features")) {
            HttpResponse<String> resultResponse = this.getComputedResultFeature(algoName);
            List<PathObject> detections = this.parseResponseToPathObjectList(resultResponse, viewerRegion);
            this.displayResult(qupath, selectedObject, detections);
            logger.info("Displaying resulting features from {}", algoName);
        } else {
            logger.warn("Unknown display for result with endpoints: {}", Arrays.toString(endpoints.toArray()));
        }

        HttpResponse<String> deletedHttpResponse = this.httpClient.deleteImageData();
        if (deletedHttpResponse.statusCode() != 204) {
            logHttpWarning(deletedHttpResponse,
                    "Image data could not be deleted from the server");
        }
    }

    /**
     * Get the selected object in the viewer (and lock it)
     *
     * @param qupathViewer
     * @return
     */
    public static PathObject getSelectedObject(QuPathViewer qupathViewer) {
        PathObject parentObject = qupathViewer.getSelectedObject();
        if (parentObject != null) parentObject.setLocked(true);
        return parentObject;
    }

    /**
     * Get the {@link ImageServer} depending on the image type and the selected channel
     * For multichannel fluo images: if a single channel is selected then the pixels of that single channel are sent,
     * if multiple channels are selected, pixels from a RGB-rendering of the image is sent
     *
     * @param qupathViewer
     * @return
     * @throws IOException
     */
    public static ImageServer<BufferedImage> getImageServer(QuPathViewer qupathViewer) throws IOException {
        // Get the selected channels
        ImageData<BufferedImage> imageData = qupathViewer.getImageData();
        ImageServer<BufferedImage> imageServer = imageData.getServer();

        ImageDisplay imageDisplay = qupathViewer.getImageDisplay();
        ObservableList<ChannelDisplayInfo> selectedChannels = imageDisplay.selectedChannels();
        ObservableList<ChannelDisplayInfo> availableChannels = imageDisplay.availableChannels();
        // Cannot get the channel from the roi, selectedObject.getROI().getC() always outputs -1 ?

        if (imageData.isFluorescence()) {
            if (selectedChannels.size() == 1) {
                // Get the image from the single channel selected
                // Getting the index because the getName() cannot be passed directly to the input of extractChannels() (e.g. "FITC (C3)" instead of "FITC")
                return new TransformedServerBuilder(imageServer)
                        .extractChannels(availableChannels.indexOf(selectedChannels.get(0)))
                        .build();
            } else {
                // Get an RGB-rendering of the multichannel display
                return PyAlgosClient.createRenderedServer(qupathViewer);
            }
        } else {
            return imageServer;
        }
    }

    /**
     * Create a rendered (RGB) {@link ImageServer} from a QuPath viewer
     * From qupath-extension-sam (https://github.com/ksugar/qupath-extension-sam/blob/20bcdbdac26014006e839f8ee295d4438d325e20/src/main/java/org/elephant/sam/Utils.java#L210)
     *
     * @param viewer
     * @return the image server
     * @throws IOException
     */
    public static ImageServer<BufferedImage> createRenderedServer(QuPathViewer viewer) throws IOException {
        return new RenderedImageServer.Builder(viewer.getImageData())
                .store(viewer.getImageRegionStore())
                .renderer(viewer.getImageDisplay())
                .build();
    }

    /**
     * Get the selected image region at full resolution
     *
     * @param qupathViewer
     * @param imageServer
     * @param selectedObject
     * @return
     */
    public static RegionRequest getRegionRequest(QuPathViewer qupathViewer, ImageServer imageServer, PathObject selectedObject) {
        Rectangle rectangle = AwtTools.getBounds(selectedObject.getROI());
        ImageRegion region = AwtTools.getImageRegion(rectangle, qupathViewer.getZPosition(), qupathViewer.getTPosition());

        RegionRequest viewerRegion = RegionRequest.createInstance(imageServer.getPath(), imageServer.getDownsampleForResolution(0), region);
        return viewerRegion.intersect2D(0, 0, imageServer.getWidth(), imageServer.getHeight());
    }

    /**
     * Add a list of objects into the image's hierarchy below their parent object
     *
     * @param qupath
     * @param parentObject
     * @param resultObjects
     */
    private void displayResult(QuPathGUI qupath, PathObject parentObject, List<PathObject> resultObjects) {
        if (resultObjects != null) {
            PathObjectHierarchy hierarchy = qupath.getViewer().getImageData().getHierarchy();
            for (PathObject obj : resultObjects) {
                if (parentObject != null) {
                    hierarchy.addObjectBelowParent(parentObject, PathObjects.createDetectionObject(obj.getROI(), obj.getPathClass(), obj.getMeasurementList()), false);
                }
                hierarchy.getSelectionModel().setSelectedObject(obj, true);
            }
            // Update the hierarchy
            hierarchy.fireHierarchyChangedEvent(hierarchy.getRootObject());
            // Update the available classification classes
            Platform.runLater(() -> {
                updateClassifications(qupath, resultObjects);
            });
        }
    }

    /**
     * Update the classification classes by adding the ones from the input list of pathObjects
     * Based on the promptToPopulateFromImage method in PathClassPane.java from qupath-gui-fx
     *
     * @param qupath
     * @param pathObjects
     */
    private void updateClassifications(QuPathGUI qupath, List<PathObject> pathObjects) {
        Set<PathClass> representedClasses = pathObjects.stream()
                .map(p -> p.getPathClass())
                .filter(p -> p != null && p != PathClass.NULL_CLASS)
                .map(p -> p.getBaseClass())
                .collect(Collectors.toSet());

        List<PathClass> newClasses = new ArrayList<>(representedClasses);
        Collections.sort(newClasses);
        if (newClasses.isEmpty()) {
            return;
        }

        newClasses.add(PathClass.StandardPathClasses.IGNORE);
        ObservableList<PathClass> availablePathClasses = qupath.getAvailablePathClasses();
        List<PathClass> currentClasses = new ArrayList<>(availablePathClasses);
        currentClasses.remove(null);
        if (currentClasses.equals(newClasses)) {
            return;
        }

        newClasses.removeAll(availablePathClasses);
        availablePathClasses.addAll(newClasses);
    }
}
