package qupath.ext.pyalgos.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;

/**
 * Class to handle the client's HTTP requests
 */
public class PyAlgosHttpClient {

    // Python API URL defined by the hostname or IP address and port of the server
    private URL apiUrl;

    // HttpClient used for all the HTTP HttpRequests
    private final HttpClient httpClient;


    public PyAlgosHttpClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Get the API URL
     */
    public URL getApiUrl() {
        return this.apiUrl;
    }

    /**
     * Set the API URL for the HTTP HttpRequests
     *
     * @param serverURL (e.g. "http://127.0.0.1:8000" as a String)
     */
    public void setURL(String serverURL) throws MalformedURLException {
        setURL(new URL(serverURL));
    }


    /**
     * Set the API URL for the HTTP requests
     *
     * @param serverURL (e.g. "http://127.0.0.1:8000" as a URL)
     */
    public void setURL(URL serverURL) {
        this.apiUrl = serverURL;
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
     * @param body Content as {@link String} to be sent in the body of the POST HttpRequest
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
     * Send an HTTP POST request to the server with a body in byte[]
     *
     * @param path  relative path appended to the apiUrl
     * @param bytes Content as {@link byte[]} to be sent in the body of the POST HttpRequest
     * @return {@link HttpResponse<String>} from the server
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private HttpResponse<String> post(String path, byte[] bytes) throws ExecutionException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + path))
                .header("Content-Type", "application/octet-stream")
                .version(HttpClient.Version.HTTP_1_1)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return this.httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).get();
    }

    /**
     * Send an HTTP DELETE request to the server
     *
     * @param path (appended to the apiUrl)
     * @return {@link HttpResponse<String>} from the server
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private HttpResponse<String> delete(String path) throws ExecutionException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + path))
                .header("Content-Type", "application/json")
                .version(HttpClient.Version.HTTP_1_1)
                .DELETE()
                .build();
        return this.httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).get();
    }


    /**
     * Check whether the client is connected to the server
     *
     * @return true if successful
     */
    public boolean isConnected() {
        try {
            HttpResponse<String> httpResponse = this.get("/");
            JsonObject response = JsonParser.parseString(httpResponse.body()).getAsJsonObject();
            return httpResponse.statusCode() == 200 && response.get("message").getAsString().equals("hello");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the list of available algorithms names
     *
     * @return {@link HttpResponse<String>} with the list of available algorithms names
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getAlgosNames() throws IOException, InterruptedException {
        return this.get("/algos_names/");
    }

    /**
     * Get info about a specific algorithm
     *
     * @param algoName
     * @return {@link HttpResponse<String>} containing the info about the algorithm (description, parameters, ...)
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getAlgoInfo(String algoName) throws IOException, InterruptedException {
        return this.get("/algos/" + algoName);
    }

    /**
     * Get the required parameters for the selected algorithm
     *
     * @param algoName
     * @return {@link HttpResponse<String>} containing the required parameters for the algorithm
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getAlgoRequiredParams(String algoName) throws IOException, InterruptedException {
        return this.get("/algos/" + algoName + "/required_parameters");
    }

    /**
     * Send a DELETE HttpRequest all the information related to the image and result on the server
     *
     * @return {@link HttpResponse<String>} from the server
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> deleteImageData() throws ExecutionException, InterruptedException {
        return this.delete("/image");
    }

    /**
     * Send a POST request for the image as a JsonObject
     *
     * @param image
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> sendImage(JsonObject image) throws ExecutionException, InterruptedException {
        return this.post("/image", new Gson().toJson(image));
    }

    /**
     * Send a POST request for the image as a byte array
     *
     * @param bytes
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> sendImage(byte[] bytes) throws ExecutionException, InterruptedException {
        return this.post("/image_bytes", bytes);
    }

    /**
     * Send a POST HttpRequest for the algo parameters for the given algoName
     *
     * @param algoName
     * @param algoParams
     * @return HTTPHttpResponse<String>
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> setAlgoParams(String algoName, String algoParams)
            throws ExecutionException, InterruptedException {
        return this.post("/image/" + algoName + "/parameters", algoParams);
    }

    /**
     * Get the parameters that were set for the selected algoName
     *
     * @param algoName
     * @return HTTPHttpResponse<String> containing the parameters for the algorithm
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getSelectedAlgoParams(String algoName) throws IOException, InterruptedException {
        return this.get("/image/" + algoName + "/parameters");
    }

    /**
     * Send the POST HttpRequest to compute the result for the given algoName. The image data & the algorithm parameters
     * should already be available on the server.
     *
     * @param algoName
     * @return HTTPHttpResponse<String>
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public HttpResponse<String> computeResult(String algoName) throws ExecutionException, InterruptedException {
        return this.post("/image/" + algoName + "/result", "result");
    }

    /**
     * Get the computed result
     *
     * @param algoName
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getComputedResult(String algoName) throws IOException, InterruptedException {
        return this.get("/image/" + algoName + "/result");
    }

    /**
     * Get the image from the result
     *
     * @param algoName
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getComputedResultImage(String algoName) throws IOException, InterruptedException {
        return this.get("/image" + algoName + "/result/image");
    }

    /**
     * Get the mask from the result
     *
     * @param algoName
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getComputedResultMask(String algoName) throws IOException, InterruptedException {
        return this.get("/image" + algoName + "/result/mask");
    }

    /**
     * Get the geojson features from the result
     *
     * @param algoName
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getComputedResultFeatures(String algoName) throws IOException, InterruptedException {
        return this.get("/image/" + algoName + "/result/features");
    }

    /**
     * Get the specific endpoint from the result
     *
     * @param algoName
     * @param endpoint
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getComputedResultEndpoint(String algoName, String endpoint)
            throws IOException, InterruptedException {
        return this.get("/image/" + algoName + "/result/" + endpoint);
    }
}
