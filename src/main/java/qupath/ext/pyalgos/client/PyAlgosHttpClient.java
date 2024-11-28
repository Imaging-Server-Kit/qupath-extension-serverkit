package qupath.ext.pyalgos.client;

import java.io.IOException;
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
     * Check whether the client is connected to the server
     *
     * @return true if successful
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
     * Get the list of available algorithms names
     *
     * @return {@link HttpResponse<String>} with the list of available algorithms names
     * @throws IOException
     * @throws InterruptedException
     */
    public HttpResponse<String> getAlgosNames() throws IOException, InterruptedException {
        return this.get("/services");
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
        return this.get("/" + algoName + "/parameters");
//        return this.get("/algos/" + algoName + "/required_parameters");
    }

    public HttpResponse<String> computeOneShot(String algoName, String parameters) throws ExecutionException, InterruptedException {
        return this.post("/" + algoName, parameters);
//        return this.post("/" + algoName + "/", parameters);
    }
}
