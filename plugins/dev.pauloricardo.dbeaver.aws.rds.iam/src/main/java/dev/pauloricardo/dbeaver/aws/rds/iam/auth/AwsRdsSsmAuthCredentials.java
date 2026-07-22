package dev.pauloricardo.dbeaver.aws.rds.iam.auth;

import org.jkiss.dbeaver.model.impl.auth.AuthModelDatabaseNativeCredentials;

public final class AwsRdsSsmAuthCredentials extends AuthModelDatabaseNativeCredentials {
    static final String PROP_CONNECTION_MODE = "awsConnectionMode";
    static final String PROP_EC2_NAME_TAG = "ec2NameTag";
    static final String PROP_RDS_NAME_TAG = "rdsNameTag";
    static final String PROP_RDS_PORT = "rdsPort";
    static final String PROP_LOCAL_PORT = "localPort";
    static final String PROP_AWS_REGION = "awsRegion";
    static final String PROP_AWS_PROFILE = "awsProfile";
    static final String PROP_AWS_CLI_PATH = "awsCliPath";
    static final String PROP_SESSION_ROLE = "sessionRole";

    static final String CONNECTION_MODE_SSM = "ssm";
    static final String DEFAULT_RDS_PORT = "5432";
    static final String DEFAULT_LOCAL_PORT = "";
    static final String DEFAULT_AWS_REGION = "us-east-1";
    static final String DEFAULT_AWS_PROFILE = "default";
    static final String DEFAULT_AWS_CLI_PATH = "aws";
    static final String DEFAULT_SESSION_ROLE = "";

    private String ec2NameTag = "";
    private String rdsNameTag = "";
    private String rdsPort = DEFAULT_RDS_PORT;
    private String localPort = DEFAULT_LOCAL_PORT;
    private String awsRegion = DEFAULT_AWS_REGION;
    private String awsProfile = DEFAULT_AWS_PROFILE;
    private String awsCliPath = DEFAULT_AWS_CLI_PATH;
    private String sessionRole = DEFAULT_SESSION_ROLE;

    public String getEc2NameTag() {
        return ec2NameTag;
    }

    public void setEc2NameTag(String ec2NameTag) {
        this.ec2NameTag = trimToEmpty(ec2NameTag);
    }

    public String getRdsNameTag() {
        return rdsNameTag;
    }

    public void setRdsNameTag(String rdsNameTag) {
        this.rdsNameTag = trimToEmpty(rdsNameTag);
    }

    public String getRdsPort() {
        return rdsPort;
    }

    public void setRdsPort(String rdsPort) {
        this.rdsPort = defaultIfBlank(rdsPort, DEFAULT_RDS_PORT);
    }

    public String getLocalPort() {
        return localPort;
    }

    public void setLocalPort(String localPort) {
        this.localPort = trimToEmpty(localPort);
    }

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
