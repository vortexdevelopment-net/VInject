package net.vortexdevelopment.plugin.vinject.version;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves latest Maven artifact versions from the VortexDevelopment repository.
 * Falls back to built-in versions if repository query fails.
 */
public class MavenVersionResolver {
    private static final Logger logger = LoggerFactory.getLogger(MavenVersionResolver.class);
    
    private static final String REPOSITORY_BASE_URL = "https://repo.vortexdevelopment.net/api/repository";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    
    // Built-in fallback versions
    private static final Map<String, String> BUILT_IN_VERSIONS = Map.of(
            "net.vortexdevelopment:VortexCore", "3.0.0",
            "net.vortexdevelopment:VortexCore-Paper", "3.0.0",
            "net.vortexdevelopment:vinject-maven-plugin", "1.0.0",
            "net.vortexdevelopment:VInject-Core", "2.0.0-SNAPSHOT",
            "net.vortexdevelopment:VInject-HTTP", "2.0.0-SNAPSHOT",
            "net.vortexdevelopment:VInject-Parent", "2.0.0-SNAPSHOT"
    );
    
    private static volatile MavenVersionResolver instance;
    private final HttpClient httpClient;
    private final Map<String, String> versionCache = new ConcurrentHashMap<>();
    
    private MavenVersionResolver() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }
    
    /**
     * Get the singleton instance of MavenVersionResolver.
     */
    @NotNull
    public static MavenVersionResolver getInstance() {
        if (instance == null) {
            synchronized (MavenVersionResolver.class) {
                if (instance == null) {
                    instance = new MavenVersionResolver();
                }
            }
        }
        return instance;
    }
    
    /**
     * Resolve the latest version for a Maven artifact.
     * Tries to fetch from repository first, falls back to built-in version on any error.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @return The resolved version (latest from repo or built-in fallback)
     */
    @NotNull
    public String resolveVersion(@NotNull String groupId, @NotNull String artifactId) {
        String cacheKey = groupId + ":" + artifactId;
        
        // Check cache first
        String cachedVersion = versionCache.get(cacheKey);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        
        // Try to fetch from repository
        try {
            String latestVersion = fetchLatestVersion(groupId, artifactId);
            if (latestVersion != null && !latestVersion.isEmpty()) {
                versionCache.put(cacheKey, latestVersion);
                logger.info("Resolved latest version for {}.{}: {}", groupId, artifactId, latestVersion);
                return latestVersion;
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch latest version for {}.{}, using built-in fallback", 
                    groupId, artifactId, e);
        }
        
        // Fall back to built-in version
        String fallbackVersion = BUILT_IN_VERSIONS.get(cacheKey);
        if (fallbackVersion != null) {
            logger.info("Using built-in fallback version for {}.{}: {}", 
                    groupId, artifactId, fallbackVersion);
            return fallbackVersion;
        }
        
        // If no built-in version exists, return a default
        logger.warn("No built-in fallback version found for {}.{}, using default", 
                groupId, artifactId);
        return "1.0.0-SNAPSHOT";
    }
    
    /**
     * Fetch the latest version from the Maven repository metadata.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @return The latest version, or null if fetch/parse fails
     */
    private String fetchLatestVersion(@NotNull String groupId, @NotNull String artifactId) throws Exception {
        String metadataUrl = buildMetadataUrl(groupId, artifactId);
        logger.debug("Fetching metadata from: {}", metadataUrl);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(metadataUrl))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        
        HttpResponse<InputStream> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofInputStream());
        
        if (response.statusCode() != 200) {
            throw new Exception("HTTP error: " + response.statusCode());
        }
        
        return parseLatestVersion(response.body());
    }
    
    /**
     * Build the metadata URL for the given groupId and artifactId.
     * Converts groupId dots to path segments.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @return The full metadata URL
     */
    @NotNull
    private String buildMetadataUrl(@NotNull String groupId, @NotNull String artifactId) {
        String groupPath = groupId.replace(".", "/");
        return String.format("%s/%s/%s/maven-metadata.xml", 
                REPOSITORY_BASE_URL, groupPath, artifactId);
    }
    
    /**
     * Parse the latest version from Maven metadata XML.
     * Prefers <release> tag, falls back to latest non-SNAPSHOT from <versions> list.
     * 
     * @param xmlInputStream The XML input stream
     * @return The latest version, or null if parsing fails
     */
    private String parseLatestVersion(@NotNull InputStream xmlInputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        var doc = builder.parse(xmlInputStream);
        
        // Try to get <release> tag first (preferred)
        var releaseNodes = doc.getElementsByTagName("release");
        if (releaseNodes.getLength() > 0) {
            String releaseVersion = releaseNodes.item(0).getTextContent().trim();
            if (!releaseVersion.isEmpty()) {
                return releaseVersion;
            }
        }
        
        // Fall back to parsing <versions> list
        var versionNodes = doc.getElementsByTagName("version");
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < versionNodes.getLength(); i++) {
            String version = versionNodes.item(i).getTextContent().trim();
            if (!version.isEmpty()) {
                versions.add(version);
            }
        }
        
        if (versions.isEmpty()) {
            throw new Exception("No versions found in metadata");
        }
        
        // Prefer non-SNAPSHOT versions, but use latest SNAPSHOT if that's all we have
        List<String> nonSnapshotVersions = versions.stream()
                .filter(v -> !v.contains("SNAPSHOT"))
                .sorted(Collections.reverseOrder())
                .toList();
        
        if (!nonSnapshotVersions.isEmpty()) {
            return nonSnapshotVersions.get(0);
        }
        
        // If only SNAPSHOT versions exist, return the latest one
        versions.sort(Collections.reverseOrder());
        return versions.get(0);
    }
}

