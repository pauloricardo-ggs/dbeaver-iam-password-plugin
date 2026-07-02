package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

final class JdbcConnectionSettings {
    static final String DEFAULT_REGION = "us-east-1";
    static final String DEFAULT_PROFILE = "default";
    static final String DEFAULT_AWS_CLI_PATH = "aws";
    static final String DEFAULT_SESSION_ROLE = "";

    private final String hostname;
    private final int port;
    private final String username;
    private final String region;
    private final String profile;
    private final String awsCliPath;
    private final String sessionRole;
    private final boolean debug;

    private JdbcConnectionSettings(
            String hostname,
            int port,
            String username,
            String region,
            String profile,
            String awsCliPath,
            String sessionRole,
            boolean debug
    ) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.region = region;
        this.profile = profile;
        this.awsCliPath = awsCliPath;
        this.sessionRole = sessionRole;
        this.debug = debug;
    }

    static JdbcConnectionSettings from(String postgresUrl, Properties properties) throws SQLException {
        ParsedJdbcUrl parsed = parse(postgresUrl);
        Map<String, String> queryParams = parseQuery(parsed.query());

        String username = firstNonBlank(
                properties.getProperty("user"),
                properties.getProperty("username"),
                queryParams.get("user"),
                queryParams.get("username")
        );
        if (isBlank(username)) {
            throw new SQLException("Missing required property 'user' for AWS RDS IAM authentication.");
        }

        String region = firstNonBlank(properties.getProperty("awsRegion"), queryParams.get("awsRegion"), DEFAULT_REGION);
        String profile = firstNonBlank(properties.getProperty("awsProfile"), queryParams.get("awsProfile"), DEFAULT_PROFILE);
        String awsCliPath = firstNonBlank(properties.getProperty("awsCliPath"), queryParams.get("awsCliPath"), DEFAULT_AWS_CLI_PATH);
        String sessionRole = firstNonBlank(
                properties.getProperty("sessionRole"),
                queryParams.get("sessionRole"),
                properties.getProperty("awsSessionRole"),
                queryParams.get("awsSessionRole"),
                DEFAULT_SESSION_ROLE
        );
        String debugValue = firstNonBlank(properties.getProperty("awsRdsIamDebug"), queryParams.get("awsRdsIamDebug"), "false");

        return new JdbcConnectionSettings(
                parsed.host(),
                parsed.port(),
                username,
                region,
                profile,
                awsCliPath,
                sessionRole,
                Boolean.parseBoolean(debugValue)
        );
    }

    String hostname() {
        return hostname;
    }

    int port() {
        return port;
    }

    String username() {
        return username;
    }

    String region() {
        return region;
    }

    String profile() {
        return profile;
    }

    String awsCliPath() {
        return awsCliPath;
    }

    String sessionRole() {
        return sessionRole;
    }

    boolean debug() {
        return debug;
    }

    private static ParsedJdbcUrl parse(String postgresUrl) throws SQLException {
        String uriValue = postgresUrl.substring(AwsRdsIamPostgresDriver.POSTGRES_URL_PREFIX.length());
        try {
            URI uri = new URI("postgresql:" + uriValue);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            if (isBlank(host)) {
                throw new SQLException("Could not parse host from JDBC URL: " + postgresUrl);
            }
            return new ParsedJdbcUrl(host, port, uri.getQuery());
        } catch (URISyntaxException e) {
            throw new SQLException("Invalid JDBC URL: " + postgresUrl, e);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new LinkedHashMap<>();
        if (isBlank(query)) {
            return values;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                values.put(decode(pair.substring(0, separator)), decode(pair.substring(separator + 1)));
            }
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ParsedJdbcUrl(String host, int port, String query) {
    }
}
