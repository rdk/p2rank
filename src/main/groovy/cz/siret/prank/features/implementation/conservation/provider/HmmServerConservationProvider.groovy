package cz.siret.prank.features.implementation.conservation.provider

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.Semaphore

/**
 * Conservation provider that fetches scores from an HMM server via HTTP POST.
 *
 * Thread-safe: uses a shared HttpClient and a Semaphore to throttle concurrent requests.
 */
@Slf4j
@CompileStatic
class HmmServerConservationProvider implements ConservationProvider {

    private final String baseUrl
    private final int timeoutSeconds
    private final HttpClient httpClient
    private final Semaphore semaphore
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create()

    HmmServerConservationProvider(String baseUrl, int timeoutSeconds, int maxConcurrentRequests) {
        this.baseUrl = baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl
        this.timeoutSeconds = timeoutSeconds
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
        this.semaphore = new Semaphore(maxConcurrentRequests)
    }

    @Override
    void checkHealth() throws ConservationProviderException {
        String endpoint = baseUrl + "/health"
        log.info "Checking conservation server health: GET {}", endpoint
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                log.info "Conservation server is healthy"
            } else {
                throw new ConservationProviderException(
                    "Conservation server health check failed: HTTP ${response.statusCode()} from $endpoint")
            }
        } catch (ConservationProviderException e) {
            throw e
        } catch (ConnectException e) {
            throw new ConservationProviderException(
                "Conservation server is not reachable at $endpoint (is the server running?)", e)
        } catch (Exception e) {
            String detail = describeException(e)
            throw new ConservationProviderException(
                "Conservation server health check failed at $endpoint: $detail", e)
        }
    }

    @Override
    String fetchScores(String sequence, String label) throws ConservationProviderException {
        String fastaContent = ">" + label + "\n" + sequence
        String jsonBody = GSON.toJson(Collections.singletonMap("fasta_content", fastaContent))

        String endpoint = baseUrl + "/conservation"
        log.info "Fetching conservation from server for [{}]: POST {}", label, endpoint
        log.debug "Request body: {}", jsonBody

        semaphore.acquire()
        long startTime = System.currentTimeMillis()
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                String body = response.body()
                if (body == null || body.trim().isEmpty()) {
                    throw new ConservationProviderException(
                        "Empty response body from server for [$label]")
                }
                long elapsed = System.currentTimeMillis() - startTime
                log.info "Fetched conservation for [{}] ({} bytes) in {}ms", label, body.length(), elapsed
                return body
            } else {
                throw new ConservationProviderException(
                    "Server returned HTTP ${response.statusCode()} for [$label]: ${response.body()}")
            }
        } catch (ConservationProviderException e) {
            throw e
        } catch (HttpTimeoutException e) {
            throw new ConservationProviderException(
                "Timeout (${timeoutSeconds}s) fetching conservation for [$label]", e)
        } catch (ConnectException e) {
            throw new ConservationProviderException(
                "Connection refused for [$label]: ${endpoint} (is the server running?)", e)
        } catch (Exception e) {
            String detail = describeException(e)
            throw new ConservationProviderException(
                "Failed to fetch conservation for [$label]: $detail", e)
        } finally {
            semaphore.release()
        }
    }

    /**
     * Build a descriptive message from an exception, handling null messages
     * and walking the cause chain if needed.
     */
    private static String describeException(Exception e) {
        String msg = e.message
        if (msg != null && !msg.isEmpty()) {
            return msg
        }
        // Try the cause chain
        Throwable cause = e.cause
        while (cause != null) {
            if (cause.message != null && !cause.message.isEmpty()) {
                return cause.class.simpleName + ": " + cause.message
            }
            cause = cause.cause
        }
        return e.class.simpleName + " (no message)"
    }

}
