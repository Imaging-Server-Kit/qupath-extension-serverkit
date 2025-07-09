package qupath.ext.serverkit.client;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.*;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ij.ImagePlus;
import ij.io.FileSaver;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import qupath.fx.dialogs.Dialogs;
import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

public class Client {
    // Logger
    private final static Logger logger = LoggerFactory.getLogger(Client.class);

    // Python API URL defined by the hostname or IP address and port of the server
    private URL apiUrl;

    // Default API URL
    public static String defaultUrl = "http://localhost:8000";

    // The client handling HTTP requests
    private final HttpClient httpClient;

    private static Client instance = new Client();

    private Client() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public static Client getInstance() {
        return instance;
    }

    public void launchHttpClient(String URL) throws IOException {
        URL = URL.trim();
        if (URL.endsWith("/")) {
            URL = URL.substring(0, URL.length() - 1);
        }
        this.apiUrl = new URL(URL);

        if (!this.isConnected())
            throw new IOException();
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
        try {
            HttpResponse<String> httpResponse = this.get("/");
            return httpResponse.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Send an HTTP GET request to the server
     *
     * @param path (appended to the apiUrl)
     * @return {@link HttpResponse<String>} from the server
     * @throws IOException
     * @throws InterruptedException
     */
    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + path))
                .header("Content-Type", "application/json").version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();
        return this.httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Send an HTTP POST request to the server with a body in String
     *
     * @param path relative path appended to the apiUrl
     * @param body Content as {@link String} to be sent in the body of the POST
     *             HttpRequest
     * @return {@link HttpResponse<String>} from the server
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private HttpResponse<String> post(String path, String body) throws ExecutionException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + path))
                .header("Content-Type", "application/json")
                .version(HttpClient.Version.HTTP_1_1)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return this.httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).get();
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
     * Parse the {@link HttpResponse}'s body as {@link JsonObject}, then parse the
     * list of {@link JsonObject} from the
     * value of the JsonObject at the given key
     *
     * @param response
     * @param key
     * @return
     * @throws IOException
     */
    public static JsonObject parseResponseToJsonObjectList(HttpResponse<String> response) throws IOException {
        JsonObject object = parseResponseToJsonObject(response);
        return object.get("properties").getAsJsonObject();
    }

    public String[] getAlgos() throws IOException, InterruptedException {
        HttpResponse<String> servicesResponse = this.get("/services");
        JsonObject algos = parseResponseToJsonObject(servicesResponse);
        JsonArray algosJsonArray = algos.get("services").getAsJsonArray();
        String[] arr = new String[algosJsonArray.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = algosJsonArray.get(i).getAsString();
        }

        return arr;
    }

    public JsonObject getParameters(String algoName) throws IOException, InterruptedException {
        HttpResponse<String> paramsResponse = this.get("/" + algoName + "/parameters");
        return parseResponseToJsonObjectList(paramsResponse);
    }

    public JsonArray getSampleImages(String algoName) throws IOException, InterruptedException {
        HttpResponse<String> sampleImagesResponse = this.get("/" + algoName + "/sample_images");
        JsonObject object = parseResponseToJsonObject(sampleImagesResponse);
        return object.get("sample_images").getAsJsonArray();
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

    public void run(QuPathGUI qupath, QuPathViewer qupathViewer, String algoName, ParameterList parameterList)
            throws ExecutionException, IOException, InterruptedException {

        Map<String, Object> parametersMap = new LinkedHashMap<>();

        // Convert parameters to JSON
        if (parameterList != null) {
            Map<String, Object> map = parameterList.getKeyValueParameters(false);
            parametersMap.putAll(map);
        }

        // Get the image within the selected annotation as JSON with "data" property and
        // b64-encoded
        PathObject selectedObject = getSelectedObject(qupathViewer);
        if (selectedObject == null) {
            Dialogs.showErrorMessage("Python algos error", "No annotation selected");
            return;
        }

        ImageServer<BufferedImage> imageServer = getImageServer(qupathViewer);
        RegionRequest viewerRegion = getRegionRequest(qupathViewer, imageServer, selectedObject);

        // This line (convert to ImagePlus) takes forever for images bigger than ~(40k, 40k)... Related to Integer.MAX_VALUE; see: https://gist.github.com/petebankhead/eff37389be8623596ef89e0d1e5a36bd
        ImagePlus img = IJTools.convertToImagePlus(imageServer, viewerRegion).getImage();


        // ImagePlus to Base64-encoded string conversion:
        byte[] serializedImage = new FileSaver(img).serialize();
        String imgEncoded = Base64.getEncoder().encodeToString(serializedImage);

        parametersMap.put("image", imgEncoded);

        String parameters = ParameterList.convertToJson(parametersMap);

        // Run the algo
        HttpResponse<String> processingResponse = this.post("/" + algoName + "/process", parameters);

        if (processingResponse.statusCode() != 201) {
            logHttpError(processingResponse, "Processing with " + algoName + " failed");
            return;
        }

        // Process the response body
        JsonArray dataTuplesList = JsonParser.parseString(processingResponse.body()).getAsJsonArray();
        for (JsonElement element : dataTuplesList) {
            JsonObject dataParams = element.getAsJsonObject().get("data_params").getAsJsonObject();
            String resultType = element.getAsJsonObject().get("type").getAsString();
            
            Gson gson = GsonTools.getInstance();
            AffineTransform transform = new AffineTransform();
            transform.translate(viewerRegion.getMinX(), viewerRegion.getMinY());
            transform.scale(viewerRegion.getDownsample(), viewerRegion.getDownsample());
            ImagePlane plane = viewerRegion.getImagePlane();
            List<PathObject> detections = new ArrayList<>();

            // Handle segmentation results returned as `features` (polygons)
            JsonArray encodedData;
            switch (resultType) {
                case "image":
                    Dialogs.showErrorMessage("Unhandled algo type", "Image filtering algorithms aren't supported");
                    break;
                case "tracks":
                    Dialogs.showErrorMessage("Unhandled algo type", "Tracking algorithms aren't supported");
                    break;
                case "mask":
                    encodedData = element.getAsJsonObject().get("data").getAsJsonArray();
                    List<PathObject> pathObjectsLabels = encodedData.asList().stream()
                            .map(e -> parsePathObject(gson, e))
                            .filter(Objects::nonNull)
                            .toList();

                    for (PathObject pathObject : pathObjectsLabels) {
                        if (!transform.isIdentity()) {
                            pathObject = PathObjectTools.transformObject(pathObject, transform, true);
                        }
                        if (plane != null && !Objects.equals(plane, pathObject.getROI().getImagePlane()))
                            pathObject = PathObjectTools.updatePlane(pathObject, plane, true, false);
                        detections.add(pathObject);
                    }
                    break;
                case "instance_mask":
                    encodedData = element.getAsJsonObject().get("data").getAsJsonArray();
                    List<PathObject> pathObjectsInstances = encodedData.asList().stream()
                            .map(e -> gson.fromJson(e.getAsJsonObject(), PathObject.class))
                            .filter(Objects::nonNull)
                            .toList();

                    for (PathObject pathObject : pathObjectsInstances) {
                        if (!transform.isIdentity()) {
                            pathObject = PathObjectTools.transformObject(pathObject, transform, true);
                        }
                        if (plane != null && !Objects.equals(plane, pathObject.getROI().getImagePlane()))
                            pathObject = PathObjectTools.updatePlane(pathObject, plane, true, false);
                        detections.add(pathObject);
                    }
                    break;
                case "points":
                    encodedData = element.getAsJsonObject().get("data").getAsJsonArray();
                    for (JsonElement pointElement : encodedData) {
                        List<Point2> pointDetections = new ArrayList<>();
                        JsonObject jsonObject = pointElement.getAsJsonObject();
                        JsonObject geometry = jsonObject.getAsJsonObject("geometry");
                        JsonArray coordinates = geometry.getAsJsonArray("coordinates");
                        double x = coordinates.get(0).getAsJsonArray().get(0).getAsDouble();
                        double y = coordinates.get(0).getAsJsonArray().get(1).getAsDouble();
                        Point2 point = new Point2(x, y);
                        pointDetections.add(point);
                        ROI pointsROI = ROIs.createPointsROI(pointDetections, plane);
                        PathObject pathObjectPoints = PathObjects.createDetectionObject(pointsROI);

                        if (!transform.isIdentity()) {
                            pathObjectPoints = PathObjectTools.transformObject(pathObjectPoints, transform, true);
                        }
                        if (plane != null && !Objects.equals(plane, pathObjectPoints.getROI().getImagePlane()))
                            pathObjectPoints = PathObjectTools.updatePlane(pathObjectPoints, plane, true, false);

                        detections.add(pathObjectPoints);
                    }
                    break;
                case "boxes":
                    // Handled just like labels
                    encodedData = element.getAsJsonObject().get("data").getAsJsonArray();
                    List<PathObject> pathObjectsBoxes = encodedData.asList().stream()
                            .map(e -> gson.fromJson(e.getAsJsonObject(), PathObject.class))
                            .filter(Objects::nonNull)
                            .toList();

                    for (PathObject pathObject : pathObjectsBoxes) {
                        if (!transform.isIdentity()) {
                            pathObject = PathObjectTools.transformObject(pathObject, transform, true);
                        }
                        if (plane != null && !Objects.equals(plane, pathObject.getROI().getImagePlane()))
                            pathObject = PathObjectTools.updatePlane(pathObject, plane, true, false);
                        detections.add(pathObject);
                    }
                    break;
                case "vectors":
                    // Handled just like labels
                    encodedData = element.getAsJsonObject().get("data").getAsJsonArray();
                    List<PathObject> pathObjectsVectors = encodedData.asList().stream()
                            .map(e -> gson.fromJson(e.getAsJsonObject(), PathObject.class))
                            .filter(Objects::nonNull)
                            .toList();

                    for (PathObject pathObject : pathObjectsVectors) {
                        if (!transform.isIdentity()) {
                            pathObject = PathObjectTools.transformObject(pathObject, transform, true);
                        }
                        if (plane != null && !Objects.equals(plane, pathObject.getROI().getImagePlane()))
                            pathObject = PathObjectTools.updatePlane(pathObject, plane, true, false);
                        detections.add(pathObject);
                    }
                    break;
                case "notification":
                    String notificationText = element.getAsJsonObject().get("data").getAsString();
                    String notificationLevel;
                    if (dataParams.has("level")) {
                        notificationLevel = dataParams.getAsJsonObject().get("level").getAsString();
                    } else {
                        notificationLevel = "info";
                    }
                    if (notificationLevel.equals("error")) {
                        Dialogs.showErrorNotification("Server notification", notificationText);
                    } else if (notificationLevel.equals("warning")) {
                        Dialogs.showWarningNotification("Server notification", notificationText);
                    } else {
                        Dialogs.showInfoNotification("Server notification", notificationText);
                    }
            }

            // Add decoded `measurements` and classification
            if (dataParams.has("features")) {
                JsonObject encodedFeatures = dataParams.getAsJsonObject().get("features").getAsJsonObject();
                for (int idx = 0; idx < detections.size(); idx++) {
                    PathObject pathObject = detections.get(idx);
                    for (String key : encodedFeatures.keySet()) {
                        if (key.equals("class")) {
                            pathObject.setPathClass(PathClass.getInstance(encodedFeatures.get(key).getAsJsonArray().get(idx).getAsString()));
                        } else {
                            try {
                                String encodedFeature = encodedFeatures.get(key).getAsString();
                                List<Float> measurements = decodeBase64TiffArray(encodedFeature);
                                if (idx < measurements.size()) {
                                    pathObject.getMeasurements().put(key, measurements.get(idx));
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                }
            }

            // Display the results
            this.displayResult(qupath, selectedObject, detections);
        }
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
        JsonObject jsonObj = element.getAsJsonObject();
        PathObject pathObject = gson.fromJson(jsonObj, PathObject.class);
        return pathObject;
    }

    /**
     * Get the selected object in the viewer (and lock it)
     *
     * @param qupathViewer
     * @return
     */
    public static PathObject getSelectedObject(QuPathViewer qupathViewer) {
        PathObject parentObject = qupathViewer.getSelectedObject();
        if (parentObject != null) // TODO: can we unlock the annotation?
            parentObject.setLocked(true);
        return parentObject;
    }

    /**
     * Get the {@link ImageServer} depending on the image type and the selected
     * channel
     * For multichannel fluo images: if a single channel is selected then the pixels
     * of that single channel are sent,
     * if multiple channels are selected, pixels from a RGB-rendering of the image
     * is sent
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
        // Cannot get the channel from the roi, selectedObject.getROI().getC() always
        // outputs -1 ?

        if (imageData.isFluorescence()) {
            if (selectedChannels.size() == 1) {
                // Get the image from the single channel selected
                // Getting the index because the getName() cannot be passed directly to the
                // input of extractChannels() (e.g. "FITC (C3)" instead of "FITC")
                return new TransformedServerBuilder(imageServer)
                        .extractChannels(availableChannels.indexOf(selectedChannels.get(0)))
                        .build();
            } else {
                // Get an RGB-rendering of the multichannel display
                return Client.createRenderedServer(qupathViewer);
            }
        } else {
            return imageServer;
        }
    }

    /**
     * Create a rendered (RGB) {@link ImageServer} from a QuPath viewer
     * From qupath-extension-sam
     * (https://github.com/ksugar/qupath-extension-sam/blob/20bcdbdac26014006e839f8ee295d4438d325e20/src/main/java/org/elephant/sam/Utils.java#L210)
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
    public static RegionRequest getRegionRequest(QuPathViewer qupathViewer, ImageServer imageServer,
            PathObject selectedObject) {
        Rectangle rectangle = AwtTools.getBounds(selectedObject.getROI());
        ImageRegion region = AwtTools.getImageRegion(rectangle, qupathViewer.getZPosition(),
                qupathViewer.getTPosition());

        RegionRequest viewerRegion = RegionRequest.createInstance(imageServer.getPath(),
                imageServer.getDownsampleForResolution(0), region);
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
                    hierarchy.addObjectBelowParent(parentObject, PathObjects.createDetectionObject(obj.getROI(),
                            obj.getPathClass(), obj.getMeasurementList()), false);
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
     * Decode base64-encoded strings representing object measurements into 1D arrays
     * of numbers
     *
     * @param base64Str
     */
    public static List<Float> decodeBase64TiffArray(String base64Str) throws Exception {
        byte[] byteArray = Base64.getDecoder().decode(base64Str);
        ByteArrayInputStream bais = new ByteArrayInputStream(byteArray);

        BufferedImage image = ImageIO.read(bais);
        if (image == null) {
            throw new IllegalArgumentException("Could not decode TIFF image from input.");
        }

        DataBuffer dataBuffer = image.getRaster().getDataBuffer();
        int size = dataBuffer.getSize();
        List<Float> values = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            values.add((float) dataBuffer.getElemDouble(i));
        }

        return values;
    }

    /**
     * Update the classification classes by adding the ones from the input list of
     * pathObjects
     * Based on the promptToPopulateFromImage method in PathClassPane.java from
     * qupath-gui-fx
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
