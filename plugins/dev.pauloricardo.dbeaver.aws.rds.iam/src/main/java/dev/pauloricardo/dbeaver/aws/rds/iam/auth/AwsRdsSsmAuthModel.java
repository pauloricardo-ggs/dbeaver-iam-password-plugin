package dev.pauloricardo.dbeaver.aws.rds.iam.auth;

import java.util.Properties;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.impl.auth.AuthModelDatabaseNative;

public final class AwsRdsSsmAuthModel extends AuthModelDatabaseNative<AwsRdsSsmAuthCredentials> {
    public static final String ID = "aws_rds_ssm";

    @NotNull
    @Override
    public AwsRdsSsmAuthCredentials createCredentials() {
        return new AwsRdsSsmAuthCredentials();
    }

    @NotNull
    @Override
    public AwsRdsSsmAuthCredentials loadCredentials(
            @NotNull DBPDataSourceContainer dataSource,
            @NotNull DBPConnectionConfiguration configuration
    ) {
        AwsRdsSsmAuthCredentials credentials = createCredentials();
        if (configuration == null) {
            return credentials;
        }
        credentials.setUserName(trimToEmpty(configuration.getUserName()));
        credentials.setEc2NameTag(property(configuration, AwsRdsSsmAuthCredentials.PROP_EC2_NAME_TAG, ""));
        credentials.setRdsNameTag(property(configuration, AwsRdsSsmAuthCredentials.PROP_RDS_NAME_TAG, ""));
        credentials.setRdsPort(property(
                configuration,
                AwsRdsSsmAuthCredentials.PROP_RDS_PORT,
                AwsRdsSsmAuthCredentials.DEFAULT_RDS_PORT
        ));
        credentials.setLocalPort(property(
                configuration,
                AwsRdsSsmAuthCredentials.PROP_LOCAL_PORT,
                AwsRdsSsmAuthCredentials.DEFAULT_LOCAL_PORT
        ));
        credentials.setSessionRole(property(
                configuration,
                AwsRdsSsmAuthCredentials.PROP_SESSION_ROLE,
                AwsRdsSsmAuthCredentials.DEFAULT_SESSION_ROLE
        ));
        credentials.setAwsCliPath(property(
                configuration,
                AwsRdsSsmAuthCredentials.PROP_AWS_CLI_PATH,
                AwsRdsSsmAuthCredentials.DEFAULT_AWS_CLI_PATH
        ));
        credentials.setAwsProfile(property(
                configuration,
                AwsRdsSsmAuthCredentials.PROP_AWS_PROFILE,
                AwsRdsSsmAuthCredentials.DEFAULT_AWS_PROFILE
        ));
        credentials.setAwsRegion(property(
                configuration,
                AwsRdsSsmAuthCredentials.PROP_AWS_REGION,
                AwsRdsSsmAuthCredentials.DEFAULT_AWS_REGION
        ));
        return credentials;
    }

    @Override
    public void saveCredentials(
            @NotNull DBPDataSourceContainer dataSource,
            @NotNull DBPConnectionConfiguration configuration,
            @NotNull AwsRdsSsmAuthCredentials credentials
    ) {
        if (configuration == null) {
            return;
        }
        AwsRdsSsmAuthCredentials safe = credentials == null ? createCredentials() : credentials;
        configuration.setUserName(trimToEmpty(safe.getUserName()));
        configuration.setUserPassword(null);
        set(configuration, AwsRdsSsmAuthCredentials.PROP_EC2_NAME_TAG, safe.getEc2NameTag());
        set(configuration, AwsRdsSsmAuthCredentials.PROP_RDS_NAME_TAG, safe.getRdsNameTag());
        set(configuration, AwsRdsSsmAuthCredentials.PROP_RDS_PORT, safe.getRdsPort());
        set(configuration, AwsRdsSsmAuthCredentials.PROP_LOCAL_PORT, safe.getLocalPort());
        set(configuration, AwsRdsSsmAuthCredentials.PROP_SESSION_ROLE, safe.getSessionRole());
        set(configuration, AwsRdsSsmAuthCredentials.PROP_AWS_CLI_PATH, safe.getAwsCliPath());
        set(configuration, AwsRdsSsmAuthCredentials.PROP_AWS_PROFILE, safe.getAwsProfile());
        set(configuration, AwsRdsSsmAuthCredentials.PROP_AWS_REGION, safe.getAwsRegion());
    }

    @Override
    public void collectConnectionProperties(
            @NotNull DBPDataSourceContainer dataSourceContainer,
            @NotNull AwsRdsSsmAuthCredentials credentials,
            @NotNull DBPConnectionConfiguration configuration,
            @NotNull Properties connectProps,
            boolean collectSecuredProps
    ) {
        if (connectProps == null) {
            return;
        }
        AwsRdsSsmAuthCredentials safe = credentials == null
                ? loadCredentials(dataSourceContainer, configuration)
                : credentials;
        put(connectProps, DBConstants.DATA_SOURCE_PROPERTY_USER, safe.getUserName());
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_CONNECTION_MODE,
                AwsRdsSsmAuthCredentials.CONNECTION_MODE_SSM);
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_EC2_NAME_TAG, safe.getEc2NameTag());
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_RDS_NAME_TAG, safe.getRdsNameTag());
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_RDS_PORT, safe.getRdsPort());
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_LOCAL_PORT, safe.getLocalPort());
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_AWS_REGION, safe.getAwsRegion());
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_AWS_PROFILE, safe.getAwsProfile());
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_AWS_CLI_PATH, safe.getAwsCliPath());
        put(connectProps, AwsRdsSsmAuthCredentials.PROP_SESSION_ROLE, safe.getSessionRole());
    }

    @Override
    public boolean isUserPasswordApplicable() {
        return false;
    }

    @Override
    protected boolean isUserPasswordNeeded(@NotNull DBPDataSourceContainer dataSourceContainer) {
        return false;
    }

    private static String property(DBPConnectionConfiguration configuration, String name, String defaultValue) {
        String authValue = configuration.getAuthProperty(name);
        if (!isBlank(authValue)) {
            return authValue.trim();
        }
        String legacyValue = configuration.getProperty(name);
        return isBlank(legacyValue) ? defaultValue : legacyValue.trim();
    }

    private static void set(DBPConnectionConfiguration configuration, String name, String value) {
        configuration.setAuthProperty(name, trimToEmpty(value));
    }

    private static void put(Properties properties, String name, String value) {
        if (!isBlank(value)) {
            properties.setProperty(name, value.trim());
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
