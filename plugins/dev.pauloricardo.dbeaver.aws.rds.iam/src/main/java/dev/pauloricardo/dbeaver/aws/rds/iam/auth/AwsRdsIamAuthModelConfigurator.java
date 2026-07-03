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

public final class AwsRdsIamAuthModelConfigurator
        implements IObjectPropertyConfigurator<DBAAuthModel<?>, DBPDataSourceContainer> {
    private Text usernameText;
    private Text awsCliPathText;
    private Text awsProfileText;
    private Text awsRegionText;
    private Text awsSessionRoleText;

    @Override
    public void createControl(
            @NotNull Composite authPanel,
            DBAAuthModel<?> object,
            @NotNull Runnable propertyChangeListener
    ) {
        usernameText = createField(authPanel, "Username", propertyChangeListener);
        awsSessionRoleText = createField(authPanel, "Session Role", propertyChangeListener);
        awsCliPathText = createField(authPanel, "AWS CLI Path", propertyChangeListener);
        awsProfileText = createField(authPanel, "AWS Profile", propertyChangeListener);
        awsRegionText = createField(authPanel, "AWS Region", propertyChangeListener);
    }

    @Override
    public void loadSettings(@NotNull DBPDataSourceContainer dataSource) {
        DBPConnectionConfiguration configuration = dataSource.getConnectionConfiguration();
        usernameText.setText(notNull(configuration.getUserName()));
        awsCliPathText.setText(firstNonBlank(
                configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH),
                configuration.getProperty(AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH),
                AwsRdsIamAuthCredentials.DEFAULT_AWS_CLI_PATH
        ));
        awsProfileText.setText(firstNonBlank(
                configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_PROFILE),
                configuration.getProperty(AwsRdsIamAuthCredentials.PROP_AWS_PROFILE),
                AwsRdsIamAuthCredentials.DEFAULT_AWS_PROFILE
        ));
        awsRegionText.setText(firstNonBlank(
                configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_REGION),
                configuration.getProperty(AwsRdsIamAuthCredentials.PROP_AWS_REGION),
                AwsRdsIamAuthCredentials.DEFAULT_AWS_REGION
        ));
        awsSessionRoleText.setText(firstNonBlank(
                configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_SESSION_ROLE),
                configuration.getProperty(AwsRdsIamAuthCredentials.PROP_SESSION_ROLE),
                configuration.getAuthProperty(AwsRdsIamAuthCredentials.PROP_LEGACY_AWS_SESSION_ROLE),
                configuration.getProperty(AwsRdsIamAuthCredentials.PROP_LEGACY_AWS_SESSION_ROLE),
                AwsRdsIamAuthCredentials.DEFAULT_SESSION_ROLE
        ));
    }

    @Override
    public void saveSettings(@NotNull DBPDataSourceContainer dataSource) {
        DBPConnectionConfiguration configuration = dataSource.getConnectionConfiguration();
        configuration.setUserName(trim(usernameText));
        configuration.setUserPassword(null);
        configuration.setAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_CLI_PATH, defaultIfBlank(
                awsCliPathText,
                AwsRdsIamAuthCredentials.DEFAULT_AWS_CLI_PATH
        ));
        configuration.setAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_PROFILE, defaultIfBlank(
                awsProfileText,
                AwsRdsIamAuthCredentials.DEFAULT_AWS_PROFILE
        ));
        configuration.setAuthProperty(AwsRdsIamAuthCredentials.PROP_AWS_REGION, defaultIfBlank(
                awsRegionText,
                AwsRdsIamAuthCredentials.DEFAULT_AWS_REGION
        ));
        configuration.setAuthProperty(
                AwsRdsIamAuthCredentials.PROP_SESSION_ROLE,
                trim(awsSessionRoleText)
        );
        dataSource.setSavePassword(true);
    }

    @Override
    public void resetSettings(@NotNull DBPDataSourceContainer dataSource) {
        loadSettings(dataSource);
    }

    @Override
    public boolean isComplete() {
        return true;
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

    private static String trim(Text text) {
        return text == null ? "" : text.getText().trim();
    }

    private static String defaultIfBlank(Text text, String defaultValue) {
        String value = trim(text);
        return value.isEmpty() ? defaultValue : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String notNull(String value) {
        return value == null ? "" : value;
    }
}
