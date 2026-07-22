package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC wrapper for PostgreSQL connections that need AWS RDS IAM authentication.
 *
 * Supported URL:
 * jdbc:awsrdsiam:postgresql://host:5432/database?sslmode=require&awsRegion=us-east-1&awsProfile=default
 *
 * The wrapper generates a fresh AWS RDS IAM auth token and delegates the real
 * connection to org.postgresql.Driver.
 */
public final class AwsRdsIamPostgresDriver implements Driver {
    public static final String URL_PREFIX = "jdbc:awsrdsiam:postgresql:";
    public static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";

    static {
        try {
            DriverManager.registerDriver(new AwsRdsIamPostgresDriver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        try {
            return connectInternal(url, info);
        } catch (SQLException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SQLException("Unexpected error in AWS RDS IAM PostgreSQL driver ("
                    + e.getClass().getName() + "): " + nullSafeMessage(e), e);
        }
    }

    private Connection connectInternal(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        Properties connectionProperties = new Properties();
        copyNonNullProperties(info, connectionProperties);

        String postgresUrl = toPostgresUrl(url);
        JdbcConnectionSettings settings = JdbcConnectionSettings.from(postgresUrl, connectionProperties);
        SsmTunnelManager.TunnelLease tunnel = null;
        String networkUrl = postgresUrl;
        JdbcConnectionSettings tokenSettings = settings;

        try {
            if (settings.usesSsmTunnel()) {
                String instanceId = new Ec2TagResolver().resolve(settings);
                RdsTagResolver.RdsEndpoint rdsEndpoint = new RdsTagResolver().resolve(settings);
                tunnel = new SsmTunnelManager().acquire(settings, instanceId, rdsEndpoint);
                tokenSettings = settings.withIamEndpoint(rdsEndpoint.hostname(), rdsEndpoint.port());
                networkUrl = JdbcConnectionSettings.withNetworkEndpoint(
                        postgresUrl, "127.0.0.1", tunnel.localPort());
            }

            String token = new AwsCliTokenGenerator().generate(tokenSettings);
            connectionProperties.setProperty("password", token);
            connectionProperties.setProperty("sslmode", connectionProperties.getProperty("sslmode", "require"));

            Connection connection = loadPostgresDriver(networkUrl).connect(networkUrl, connectionProperties);
            if (connection == null) {
                throw new SQLException("PostgreSQL JDBC driver did not accept URL: " + networkUrl);
            }
            applySessionRole(connection, settings.sessionRole());
            if (tunnel != null) {
                return TunneledConnection.wrap(connection, tunnel);
            }
            return connection;
        } catch (SQLException | RuntimeException e) {
            if (tunnel != null) {
                tunnel.close();
            }
            throw e;
        }
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[] {
                property("user", true, "Database user mapped to the IAM principal"),
                property("awsRegion", false, "AWS region. Default: us-east-1"),
                property("awsProfile", false, "AWS CLI profile. Default: default"),
                property("awsCliPath", false, "AWS CLI binary path. Default: aws"),
                property("sessionRole", false, "Optional PostgreSQL role to apply with SET ROLE after connecting"),
                property("ec2NameTag", false, "EC2 Name tag used by AWS RDS SSM authentication"),
                property("rdsNameTag", false, "RDS Name tag used by AWS RDS SSM authentication"),
                property("rdsPort", false, "Remote RDS port used by the SSM tunnel"),
                property("localPort", false, "Local port exposed by the SSM tunnel"),
                property("awsRdsIamDebug", false, "Print token generation diagnostics without token value")
        };
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 1;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Parent logger is not supported.");
    }

    private static DriverPropertyInfo property(String name, boolean required, String description) {
        DriverPropertyInfo propertyInfo = new DriverPropertyInfo(name, null);
        propertyInfo.required = required;
        propertyInfo.description = description;
        return propertyInfo;
    }

    private static String toPostgresUrl(String url) {
        return POSTGRES_URL_PREFIX + url.substring(URL_PREFIX.length());
    }

    private static void copyNonNullProperties(Properties source, Properties target) {
        if (source == null) {
            return;
        }

        for (String propertyName : source.stringPropertyNames()) {
            String value = source.getProperty(propertyName);
            if (value != null) {
                target.setProperty(propertyName, value);
            }
        }
    }

    private static void applySessionRole(Connection connection, String sessionRole) throws SQLException {
        if (sessionRole == null || sessionRole.isBlank()) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE " + quoteIdentifier(sessionRole.trim()));
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String nullSafeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "no detail message" : message;
    }

    private static Driver loadPostgresDriver(String postgresUrl) throws SQLException {
        try {
            return DriverManager.getDriver(postgresUrl);
        } catch (SQLException ignored) {
            // Fall back to direct class loading below. DBeaver may expose the PostgreSQL
            // driver either through DriverManager or through the driver library classpath.
        }

        try {
            Class<?> driverClass = Class.forName("org.postgresql.Driver");
            return (Driver) driverClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new SQLException(
                    "PostgreSQL JDBC driver was not found. Add the PostgreSQL driver to the DBeaver driver libraries. Cause: " + e.getMessage()
            );
        }
    }
}
