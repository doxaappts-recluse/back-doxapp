package pe.dcs.app.service.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import org.springframework.core.io.buffer.DataBuffer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupabaseStorageService {

    private final SupabaseProperties props;
    private final WebClient webClient;

    // -------------------------
    // UPLOAD
    // -------------------------
    public void upload(
            InputStream inputStream,
            String bucketKey,
            String path,
            String contentType
    ) {

        String bucket = resolveBucket(bucketKey);

        Flux<DataBuffer> body = DataBufferUtils.readInputStream(
                () -> inputStream,
                new DefaultDataBufferFactory(),
                4096
        );

        webClient.post()
                .uri(props.getUrl()
                        + "/storage/v1/object/"
                        + bucket
                        + "/"
                        + path)
                .header("Authorization", "Bearer " + props.getServiceKey())
                .header("apikey", props.getServiceKey())
                .header("x-upsert", "true")
                .header("Content-Type",
                        contentType != null ? contentType : "application/octet-stream")
                .body(BodyInserters.fromDataBuffers(body))
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        resp -> resp.bodyToMono(String.class)
                                .map(err -> new RuntimeException("Upload failed: " + err))
                )
                .bodyToMono(Void.class)
                .block();
    }

    // -------------------------
    // DOWNLOAD
    // -------------------------
    public InputStream download(String bucketKey, String path) {

        String bucket = resolveBucket(bucketKey);

        byte[] fileBytes = webClient.get()
                .uri(props.getUrl()
                        + "/storage/v1/object/authenticated/"
                        + bucket
                        + "/"
                        + path)
                .header("Authorization", "Bearer " + props.getServiceKey())
                .header("apikey", props.getServiceKey())
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException("Supabase error: " + body))
                )
                .bodyToMono(byte[].class)
                .block();

        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException(
                    "File not found or empty in Supabase: " + bucket + "/" + path
            );
        }

        return new ByteArrayInputStream(fileBytes);
    }

    public void debugDownload(String bucketKey, String path) {

        String bucket = resolveBucket(bucketKey);

        webClient.get()
                .uri(props.getUrl()
                        + "/storage/v1/object/authenticated/"
                        + bucket
                        + "/"
                        + path)
                .header("Authorization", "Bearer " + props.getServiceKey())
                .header("apikey", props.getServiceKey())
                .exchangeToMono(response -> {

                    System.out.println("HTTP STATUS: " + response.statusCode());

                    System.out.println("HEADERS:");
                    response.headers().asHttpHeaders().forEach((k, v) ->
                            System.out.println(k + " = " + v)
                    );

                    return response.bodyToMono(String.class);
                })
                .doOnNext(body -> {
                    System.out.println("RAW BODY:");
                    System.out.println(body);
                })
                .block();
    }

    // -------------------------
    // SIGNED URL
    // -------------------------
    public String createSignedUrl(
            String bucketKey,
            String path,
            int expiresInSeconds
    ) {

        String bucket = resolveBucket(bucketKey);

        String json = webClient.post()
                .uri(props.getUrl()
                        + "/storage/v1/object/sign/"
                        + bucket
                        + "/"
                        + path)
                .header("Authorization", "Bearer " + props.getServiceKey())
                .header("apikey", props.getServiceKey())
                .bodyValue(Map.of("expiresIn", expiresInSeconds))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractUrl(json);
    }

    // -------------------------
    // BUCKET RESOLVER
    // -------------------------
    private String resolveBucket(String key) {
        String bucket = props.getStorage().getBuckets().get(key);

        if (bucket == null) {
            throw new IllegalArgumentException("Bucket not found for key: " + key);
        }

        return bucket;
    }

    // -------------------------
    // JSON PARSER
    // -------------------------
    private String extractUrl(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);
            return node.get("signedURL").asText();
        } catch (Exception e) {
            throw new RuntimeException("Error parsing signed URL", e);
        }
    }
}