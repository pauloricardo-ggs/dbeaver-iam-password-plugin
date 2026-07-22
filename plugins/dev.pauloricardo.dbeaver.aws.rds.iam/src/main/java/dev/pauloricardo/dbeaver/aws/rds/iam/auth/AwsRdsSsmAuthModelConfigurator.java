package dev.pauloricardo.dbeaver.aws.rds.iam.auth;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.access.DBAAuthModel;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.ui.IObjectPropertyConfigurator;

public final class AwsRdsSsmAuthModelConfigurator
        implements IObjectPropertyConfigurator<DBAAuthModel<?>, DBPDataSourceContainer> {
    private Text ec2NameTagText;
    private Text rdsNameTagText;
    private Text rdsPortText;
    private Text localPortText;
    private Text usernameText;
    private Text sessionRoleText;
    private Text awsCliPathText;
    private Text awsProfileText;
    private Text awsRegionText;

    @Override
    public void createControl(
            @NotNull Composite authPanel,
            DBAAuthModel<?> object,
            @NotNull Runnable propertyChangeListener
    ) {
        ec2NameTagText = createField(authPanel, "EC2 Tag", propertyChangeListener);
        rdsNameTagText = createField(authPanel, "RDS Tag", propertyChangeListener);
        rdsPortText = createField(authPanel, "RDS Port", propertyChangeListener);
        localPortText = createField(authPanel, "Local Port", propertyChangeListener);
        usernameText = createField(authPanel, "Username", propertyChangeListener);
        sessionRoleText = createField(authPanel, "Session Role", propertyChangeListener);
        awsCliPathText = createField(authPanel, "AWS CLI Path", propertyChangeListener);
        awsProfileText = createField(authPanel, "AWS Profile", propertyChangeListener);
        awsRegionText = createField(authPanel, "AWS Region", propertyChangeListener);
    }

    @Override
    public void loadSettings(@NotNull DBPDataSourceContainer dataSource) {
        DBPConnectionConfiguration configuration = dataSource.getConnectionConfiguration();
        ec2NameTagText.setText(property(configuration, AwsRdsSsmAuthCredentials.PROP_EC2_NAME_TAG, ""));
        rdsNameTagText.setText(property(configuration, AwsRdsSsmAuthCredentials.PROP_RDS_NAME_TAG, ""));
        rdsPortText.setText(property(
                configuration, AwsRdsSsmAuthCredentials.PROP_RDS_PORT, AwsRdsSsmAuthCredentials.DEFAULT_RDS_PORT));
        localPortText.setText(property(
                configuration, AwsRdsSsmAuthCredentials.PROP_LOCAL_PORT, AwsRdsSsmAuthCredentials.DEFAULT_LOCAL_PORT));
        usernameText.setText(notNull(configuration.getUserName()));
        sessionRoleText.setText(property(
                configuration, AwsRdsSsmAuthCredentials.PROP_SESSION_ROLE, AwsRdsSsmAuthCredentials.DEFAULT_SESSION_ROLE));
        awsCliPathText.setText(property(
                configuration, AwsRdsSsmAuthCredentials.PROP_AWS_CLI_PATH, AwsRdsSsmAuthCredentials.DEFAULT_AWS_CLI_PATH));
        awsProfileText.setText(property(
                configuration, AwsRdsSsmAuthCredentials.PROP_AWS_PROFILE, AwsRdsSsmAuthCredentials.DEFAULT_AWS_PROFILE));
        awsRegionText.setText(property(
                configuration, AwsRdsSsmAuthCredentials.PROP_AWS_REGION, AwsRdsSsmAuthCredentials.DEFAULT_AWS_REGION));
    }

    @Override
    public void saveSettings(@NotNull DBPDataSourceContainer dataSource) {
        DBPConnectionConfiguration configuration = dataSource.getConnectionConfiguration();
        configuration.setUserName(trim(usernameText));
        configuration.setUserPassword(null);
        set(configuration, AwsRdsSsmAuthCredentials.PROP_EC2_NAME_TAG, trim(ec2NameTagText));
        set(configuration, AwsRdsSsmAuthCredentials.PROP_RDS_NAME_TAG, trim(rdsNameTagText));
        set(configuration, AwsRdsSsmAuthCredentials.PROP_RDS_PORT,
                defaultIfBlank(rdsPortText, AwsRdsSsmAuthCredentials.DEFAULT_RDS_PORT));
        set(configuration, AwsRdsSsmAuthCredentials.PROP_LOCAL_PORT, trim(localPortText));
        set(configuration, AwsRdsSsmAuthCredentials.PROP_SESSION_ROLE, trim(sessionRoleText));
        set(configuration, AwsRdsSsmAuthCredentials.PROP_AWS_CLI_PATH,
                defaultIfBlank(awsCliPathText, AwsRdsSsmAuthCredentials.DEFAULT_AWS_CLI_PATH));
        set(configuration, AwsRdsSsmAuthCredentials.PROP_AWS_PROFILE,
                defaultIfBlank(awsProfileText, AwsRdsSsmAuthCredentials.DEFAULT_AWS_PROFILE));
        set(configuration, AwsRdsSsmAuthCredentials.PROP_AWS_REGION,
                defaultIfBlank(awsRegionText, AwsRdsSsmAuthCredentials.DEFAULT_AWS_REGION));
        dataSource.setSavePassword(true);
    }

    @Override
    public void resetSettings(@NotNull DBPDataSourceContainer dataSource) {
        loadSettings(dataSource);
    }

    @Override
    public boolean isComplete() {
        return !trim(ec2NameTagText).isEmpty()
                && !trim(rdsNameTagText).isEmpty()
                && !trim(usernameText).isEmpty()
                && validPort(trim(rdsPortText))
                && validPort(trim(localPortText));
    }

    private static Text createField(Composite parent, String label, Runnable propertyChangeListener) {
        Label fieldLabel = new Label(parent, SWT.NONE);
        fieldLabel.setText(label + ":");
        fieldLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));

        Text text = new Text(parent, SWT.BORDER);
        GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
        gridData.widthHint = 320;
        text.setLayoutData(gridData);
        text.addModifyListener(event -> propertyChangeListener.run());
        return text;
    }

    private static String property(DBPConnectionConfiguration configuration, String name, String defaultValue) {
        String authValue = configuration.getAuthProperty(name);
        if (authValue != null && !authValue.trim().isEmpty()) {
            return authValue.trim();
        }
        String value = configuration.getProperty(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static void set(DBPConnectionConfiguration configuration, String name, String value) {
        configuration.setAuthProperty(name, value);
    }

    private static String trim(Text text) {
        return text == null ? "" : text.getText().trim();
    }

    private static String defaultIfBlank(Text text, String defaultValue) {
        String value = trim(text);
        return value.isEmpty() ? defaultValue : value;
    }

    private static boolean validPort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String notNull(String value) {
        return value == null ? "" : value;
    }
}
