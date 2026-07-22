package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.sql.SQLException;
import java.util.List;

final class Ec2TagResolver {
    String resolve(JdbcConnectionSettings settings) throws SQLException {
        List<String> arguments = AwsCliCommand.commonArguments(
                settings,
                "ec2", "describe-instances",
                "--filters",
                "[{\"Name\":\"tag:Name\",\"Values\":[\""
                        + AwsCliCommand.jsonEscape(settings.ec2NameTag())
                        + "\"]},{\"Name\":\"instance-state-name\",\"Values\":[\"running\"]}]",
                "--query", "Reservations[].Instances[].InstanceId",
                "--output", "text"
        );
        String output = AwsCliCommand.execute(settings, arguments, "EC2 lookup by Name tag");
        List<String> instanceIds = AwsOutputValues.split(output);
        if (instanceIds.isEmpty()) {
            throw new SQLException("No running EC2 instance was found with Name tag '"
                    + settings.ec2NameTag() + "' in region " + settings.region() + ".");
        }
        if (instanceIds.size() > 1) {
            throw new SQLException("More than one running EC2 instance was found with Name tag '"
                    + settings.ec2NameTag() + "': " + String.join(", ", instanceIds)
                    + ". Make the Name tag unique.");
        }
        return instanceIds.get(0);
    }
}
