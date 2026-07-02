package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
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
        if (!acceptsURL(url)) {
            return null;
        }

        Properties connectionProperties = new Properties();
        if (info != null) {
            connectionProperties.putAll(info);
        }

        String postgresUrl = toPostgresUrl(url);
        JdbcConnectionSettings settings = JdbcConnectionSettings.from(postgresUrl, connectionProperties);
        String token = new AwsCliTokenGenerator().generate(settings);

        connectionProperties.setProperty("password", token);
        connectionProperties.setProperty("sslmode", connectionProperties.getProperty("sslmode", "require"));

        return loadPostgresDriver(postgresUrl).connect(postgresUrl, connectionProperties);
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
