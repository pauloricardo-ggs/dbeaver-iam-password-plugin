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
        credentials.setUserName(configuration.getUserName());
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
        configuration.setUserName(credentials.getUserName());
        configuration.setUserPassword(null);
        configuration.setAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_REGION, credentials.getAwsRegion());
        configuration.setAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_PROFILE, credentials.getAwsProfile());
        configuration.setAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH, credentials.getAwsCliPath());
        configuration.setAuthProperty(AwsRdsIamAuthCredentials.PROP_SESSION_ROLE, credentials.getSessionRole());
    }

    @Override
    public void collectConnectionProperties(
            @NotNull DBPDataSourceContainer dataSourceContainer,
            @NotNull AwsRdsIamAuthCredentials credentials,
            @NotNull DBPConnectionConfiguration configuration,
            @NotNull Properties connectProps,
            boolean collectSecuredProps
    ) {
        if (!isBlank(credentials.getUserName())) {
            connectProps.put(DBConstants.DATA_SOURCE_PROPERTY_USER, credentials.getUserName());
        }
        connectProps.put(AwsRdsIamAuthCredentials.PROP_AWS_REGION, credentials.getAwsRegion());
        connectProps.put(AwsRdsIamAuthCredentials.PROP_AWS_PROFILE, credentials.getAwsProfile());
        connectProps.put(AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH, credentials.getAwsCliPath());
        if (!isBlank(credentials.getSessionRole())) {
            connectProps.put(AwsRdsIamAuthCredentials.PROP_SESSION_ROLE, credentials.getSessionRole());
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
