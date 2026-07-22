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
    static final String CONNECTION_MODE_SSM = "ssm";

    private final String hostname;
    private final int port;
    private final String username;
    private final String region;
    private final String profile;
    private final String awsCliPath;
    private final String sessionRole;
    private final boolean debug;
    private final String connectionMode;
    private final String ec2NameTag;
    private final String rdsNameTag;
    private final int rdsPort;
    private final int localPort;

    private JdbcConnectionSettings(
            String hostname,
            int port,
            String username,
            String region,
            String profile,
            String awsCliPath,
            String sessionRole,
            boolean debug,
            String connectionMode,
            String ec2NameTag,
            String rdsNameTag,
            int rdsPort,
            int localPort
    ) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.region = region;
        this.profile = profile;
        this.awsCliPath = awsCliPath;
        this.sessionRole = sessionRole;
        this.debug = debug;
        this.connectionMode = connectionMode;
        this.ec2NameTag = ec2NameTag;
        this.rdsNameTag = rdsNameTag;
        this.rdsPort = rdsPort;
        this.localPort = localPort;
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
        String connectionMode = firstNonBlank(properties.getProperty("awsConnectionMode"), "direct");
        String ec2NameTag = firstNonBlank(properties.getProperty("ec2NameTag"), "");
        String rdsNameTag = firstNonBlank(properties.getProperty("rdsNameTag"), "");
        int rdsPort = parsePort(firstNonBlank(properties.getProperty("rdsPort"), "5432"), "RDS Port");
        int localPort = parseOptionalPort(properties.getProperty("localPort"), "Local Port");

        if (CONNECTION_MODE_SSM.equals(connectionMode)) {
            if (isBlank(ec2NameTag)) {
                throw new SQLException("Missing required AWS RDS SSM property 'EC2 Name Tag'.");
            }
            if (isBlank(rdsNameTag)) {
                throw new SQLException("Missing required AWS RDS SSM property 'RDS Name Tag'.");
            }
            if (localPort == 0) {
                throw new SQLException("Missing required AWS RDS SSM property 'Local Port'.");
            }
        }

        return new JdbcConnectionSettings(
                parsed.host(),
                parsed.port(),
                username,
                region,
                profile,
                awsCliPath,
                sessionRole,
                Boolean.parseBoolean(debugValue),
                connectionMode,
                ec2NameTag,
                rdsNameTag,
                rdsPort,
                localPort
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

    boolean usesSsmTunnel() {
        return CONNECTION_MODE_SSM.equals(connectionMode);
    }

    String ec2NameTag() {
        return ec2NameTag;
    }

    String rdsNameTag() {
        return rdsNameTag;
    }

    int rdsPort() {
        return rdsPort;
    }

    int localPort() {
        return localPort;
    }

    JdbcConnectionSettings withIamEndpoint(String hostname, int port) {
        return new JdbcConnectionSettings(
                hostname,
                port,
                username,
                region,
                profile,
                awsCliPath,
                sessionRole,
                debug,
                connectionMode,
                ec2NameTag,
                rdsNameTag,
                rdsPort,
                localPort
        );
    }

    static String withNetworkEndpoint(String postgresUrl, String hostname, int port) throws SQLException {
        String uriValue = postgresUrl.substring(AwsRdsIamPostgresDriver.POSTGRES_URL_PREFIX.length());
        try {
            URI uri = new URI("postgresql:" + uriValue);
            URI rewritten = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    hostname,
                    port,
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
            return "jdbc:" + rewritten;
        } catch (URISyntaxException e) {
            throw new SQLException("Invalid JDBC URL: " + postgresUrl, e);
        }
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

    private static int parseOptionalPort(String value, String label) throws SQLException {
        return isBlank(value) ? 0 : parsePort(value, label);
    }

    private static int parsePort(String value, String label) throws SQLException {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                throw new SQLException(label + " must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new SQLException(label + " must be a valid port number.", e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
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
