package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.util.Arrays;
import java.util.List;

final class AwsOutputValues {
    private AwsOutputValues() {
    }

    static List<String> split(String output) {
        if (output == null || output.isBlank() || "None".equals(output.trim())) {
            return List.of();
        }
        return Arrays.stream(output.trim().split("\\s+"))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
