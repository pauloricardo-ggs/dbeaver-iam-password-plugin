package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.sql.SQLException;
import java.util.List;

final class RdsTagResolver {
    RdsEndpoint resolve(JdbcConnectionSettings settings) throws SQLException {
        List<String> tagArguments = AwsCliCommand.commonArguments(
                settings,
                "resourcegroupstaggingapi", "get-resources",
                "--resource-type-filters", "rds:db",
                "--tag-filters", "Key=Name,Values=" + settings.rdsNameTag(),
                "--query", "ResourceTagMappingList[].ResourceARN",
                "--output", "text"
        );
        String tagOutput = AwsCliCommand.execute(settings, tagArguments, "RDS lookup by Name tag");
        List<String> resourceArns = AwsOutputValues.split(tagOutput);
        if (resourceArns.isEmpty()) {
            throw new SQLException("No RDS DB instance was found with Name tag '"
                    + settings.rdsNameTag() + "' in region " + settings.region() + ".");
        }
        if (resourceArns.size() > 1) {
            throw new SQLException("More than one RDS DB instance was found with Name tag '"
                    + settings.rdsNameTag() + "'. Make the Name tag unique.");
        }

        List<String> describeArguments = AwsCliCommand.commonArguments(
                settings,
                "rds", "describe-db-instances",
                "--db-instance-identifier", resourceArns.get(0),
                "--query", "DBInstances[0].[Endpoint.Address,DBInstanceStatus,Engine]",
                "--output", "text"
        );
        String describeOutput = AwsCliCommand.execute(settings, describeArguments, "RDS endpoint lookup");
        List<String> values = AwsOutputValues.split(describeOutput);
        if (values.size() < 3 || "None".equals(values.get(0))) {
            throw new SQLException("AWS CLI returned incomplete endpoint information for RDS Name tag '"
                    + settings.rdsNameTag() + "'.");
        }
        if (!"available".equals(values.get(1))) {
            throw new SQLException("RDS DB instance with Name tag '" + settings.rdsNameTag()
                    + "' is not available. Current status: " + values.get(1) + ".");
        }
        if (!values.get(2).contains("postgres")) {
            throw new SQLException("RDS DB instance with Name tag '" + settings.rdsNameTag()
                    + "' is not PostgreSQL. Engine: " + values.get(2) + ".");
        }
        return new RdsEndpoint(values.get(0), settings.rdsPort());
    }

    record RdsEndpoint(String hostname, int port) {
    }
}
