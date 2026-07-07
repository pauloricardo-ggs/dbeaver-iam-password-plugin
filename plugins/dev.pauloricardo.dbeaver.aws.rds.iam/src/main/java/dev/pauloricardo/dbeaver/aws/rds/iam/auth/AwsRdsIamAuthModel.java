package dev.pauloricardo.dbeaver.aws.rds.iam.auth;

import java.util.Properties;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.impl.auth.AuthModelDatabaseNative;

public final class AwsRdsIamAuthModel extends AuthModelDatabaseNative<AwsRdsIamAuthCredentials> {
    public static final String ID = "aws_rds_iam";

    @NotNull
    @Override
    public AwsRdsIamAuthCredentials createCredentials() {
        return new AwsRdsIamAuthCredentials();
    }

    @NotNull
    @Override
    public AwsRdsIamAuthCredentials loadCredentials(
            @NotNull DBPDataSourceContainer dataSource,
            @NotNull DBPConnectionConfiguration configuration
    ) {
        AwsRdsIamAuthCredentials credentials = createCredentials();
        if (configuration == null) {
            return credentials;
        }

        credentials.setUserName(trimToEmpty(configuration.getUserName()));
        credentials.setAwsRegion(firstNonBlank(
                configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_REGION),
                configuration.getProperty(AwsRdsIamAuthCredentials.PROP_AWS_REGION),
                AwsRdsIamAuthCredentials.DEFAULT_AWS_REGION
        ));
        credentials.setAwsProfile(firstNonBlank(
                configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_PROFILE),
                configuration.getProperty(AwsRdsIamAuthCredentials.PROP_AWS_PROFILE),
                AwsRdsIamAuthCredentials.DEFAULT_AWS_PROFILE
        ));
        credentials.setAwsCliPath(firstNonBlank(
                configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH),
                configuration.getProperty(AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH),
                AwsRdsIamAuthCredentials.DEFAULT_AWS_CLI_PATH
        ));
        credentials.setSessionRole(firstNonBlank(
            configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_SESSION_ROLE),
            configuration.getProperty(AwsRdsIamAuthCredentials.PROP_SESSION_ROLE),
            configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_LEGACY_AWS_SESSION_ROLE),
            configuration.getProperty(AwsRdsIamAuthCredentials.PROP_LEGACY_AWS_SESSION_ROLE),
            AwsRdsIamAuthCredentials.DEFAULT_SESSION_ROLE
        ));
        return credentials;
    }

    @Override
    public void saveCredentials(
            @NotNull DBPDataSourceContainer dataSource,
            @NotNull DBPConnectionConfiguration configuration,
            @NotNull AwsRdsIamAuthCredentials credentials
    ) {
        if (configuration == null) {
            return;
        }

        AwsRdsIamAuthCredentials safeCredentials = credentials == null ? createCredentials() : credentials;
        configuration.setUserName(trimToEmpty(safeCredentials.getUserName()));
        configuration.setUserPassword(null);
        configuration.setAuthProperty(
                AwsRdsIamAuthCredentials.PROP_AWS_REGION,
                defaultIfBlank(safeCredentials.getAwsRegion(), AwsRdsIamAuthCredentials.DEFAULT_AWS_REGION)
        );
        configuration.setAuthProperty(
                AwsRdsIamAuthCredentials.PROP_AWS_PROFILE,
                defaultIfBlank(safeCredentials.getAwsProfile(), AwsRdsIamAuthCredentials.DEFAULT_AWS_PROFILE)
        );
        configuration.setAuthProperty(
                AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH,
                defaultIfBlank(safeCredentials.getAwsCliPath(), AwsRdsIamAuthCredentials.DEFAULT_AWS_CLI_PATH)
        );
        configuration.setAuthProperty(
                AwsRdsIamAuthCredentials.PROP_SESSION_ROLE,
                trimToEmpty(safeCredentials.getSessionRole())
        );
    }

    @Override
    public void collectConnectionProperties(
            @NotNull DBPDataSourceContainer dataSourceContainer,
            @NotNull AwsRdsIamAuthCredentials credentials,
            @NotNull DBPConnectionConfiguration configuration,
            @NotNull Properties connectProps,
            boolean collectSecuredProps
    ) {
        if (connectProps == null) {
            return;
        }

        AwsRdsIamAuthCredentials safeCredentials = credentials == null
                ? loadCredentials(dataSourceContainer, configuration)
                : credentials;

        if (!isBlank(safeCredentials.getUserName())) {
            connectProps.put(DBConstants.DATA_SOURCE_PROPERTY_USER, safeCredentials.getUserName());
        }
        connectProps.put(
                AwsRdsIamAuthCredentials.PROP_AWS_REGION,
                defaultIfBlank(safeCredentials.getAwsRegion(), AwsRdsIamAuthCredentials.DEFAULT_AWS_REGION)
        );
        connectProps.put(
                AwsRdsIamAuthCredentials.PROP_AWS_PROFILE,
                defaultIfBlank(safeCredentials.getAwsProfile(), AwsRdsIamAuthCredentials.DEFAULT_AWS_PROFILE)
        );
        connectProps.put(
                AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH,
                defaultIfBlank(safeCredentials.getAwsCliPath(), AwsRdsIamAuthCredentials.DEFAULT_AWS_CLI_PATH)
        );
        if (!isBlank(safeCredentials.getSessionRole())) {
            connectProps.put(AwsRdsIamAuthCredentials.PROP_SESSION_ROLE, safeCredentials.getSessionRole());
        }
    }

    @Override
    public boolean isUserPasswordApplicable() {
        return false;
    }

    @Override
    protected boolean isUserPasswordNeeded(@NotNull DBPDataSourceContainer dataSourceContainer) {
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
