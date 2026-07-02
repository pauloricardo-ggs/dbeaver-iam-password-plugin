package dev.pauloricardo.dbeaver.aws.rds.iam.auth;

import org.jkiss.dbeaver.model.impl.auth.AuthModelDatabaseNativeCredentials;

public final class AwsRdsIamAuthCredentials extends AuthModelDatabaseNativeCredentials {
    static final String PROP_AWS_REGION = "awsRegion";
    static final String PROP_AWS_PROFILE = "awsProfile";
    static final String PROP_AWS_CLI_PATH = "awsCliPath";
    static final String PROP_SESSION_ROLE = "sessionRole";
    static final String PROP_LEGACY_AWS_SESSION_ROLE = "awsSessionRole";

    static final String DEFAULT_AWS_REGION = "us-east-1";
    static final String DEFAULT_AWS_PROFILE = "default";
    static final String DEFAULT_AWS_CLI_PATH = "aws";
    static final String DEFAULT_SESSION_ROLE = "";

    private String awsRegion = DEFAULT_AWS_REGION;
    private String awsProfile = DEFAULT_AWS_PROFILE;
    private String awsCliPath = DEFAULT_AWS_CLI_PATH;
    private String sessionRole = DEFAULT_SESSION_ROLE;

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = defaultIfBlank(awsRegion, DEFAULT_AWS_REGION);
    }

    public String getAwsProfile() {
        return awsProfile;
    }

    public void setAwsProfile(String awsProfile) {
        this.awsProfile = defaultIfBlank(awsProfile, DEFAULT_AWS_PROFILE);
    }

    public String getAwsCliPath() {
        return awsCliPath;
    }

    public void setAwsCliPath(String awsCliPath) {
        this.awsCliPath = defaultIfBlank(awsCliPath, DEFAULT_AWS_CLI_PATH);
    }

    public String getSessionRole() {
        return sessionRole;
    }

    public void setSessionRole(String sessionRole) {
        this.sessionRole = trimToEmpty(sessionRole);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
