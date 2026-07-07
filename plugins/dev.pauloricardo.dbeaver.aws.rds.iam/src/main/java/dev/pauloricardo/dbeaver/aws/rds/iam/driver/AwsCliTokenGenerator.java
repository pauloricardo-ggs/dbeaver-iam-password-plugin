package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class AwsCliTokenGenerator {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    String generate(JdbcConnectionSettings settings) throws SQLException {
        List<String> command = tokenCommand(settings.awsCliPath(), settings);

        if (settings.debug()) {
            System.err.println("[dbeaver-aws-rds-iam-auth] Generating token for "
                    + settings.username() + "@" + settings.hostname() + ":" + settings.port()
                    + " region=" + settings.region() + " profile=" + settings.profile()
                    + " awsCliPath=" + command.get(0));
        }

        try {
            return execute(command, settings);
        } catch (IOException e) {
            if (!isDefaultAwsCliPath(settings.awsCliPath())) {
                throw new SQLException("Could not execute AWS CLI. Check awsCliPath. Command: " + command.get(0), e);
            }

            IOException lastException = e;
            for (String fallbackPath : defaultAwsCliCandidates()) {
                if (!new File(fallbackPath).canExecute()) {
                    continue;
                }
                List<String> fallbackCommand = tokenCommand(fallbackPath, settings);
                try {
                    return execute(fallbackCommand, settings);
                } catch (IOException fallbackException) {
                    lastException = fallbackException;
                }
            }

            throw new SQLException("Could not execute AWS CLI. Set awsCliPath to the full AWS CLI binary path, "
                    + "for example /opt/homebrew/bin/aws on Apple Silicon Homebrew or "
                    + "C:\\Program Files\\Amazon\\AWSCLIV2\\aws.exe on Windows. Command: " + command.get(0), lastException);
        }
    }

    private static List<String> tokenCommand(String awsCliPath, JdbcConnectionSettings settings) {
        List<String> command = new ArrayList<>();
        command.add(awsCliPath);
        command.add("rds");
        command.add("generate-db-auth-token");
        command.add("--profile");
        command.add(settings.profile());
        command.add("--hostname");
        command.add(settings.hostname());
        command.add("--port");
        command.add(String.valueOf(settings.port()));
        command.add("--region");
        command.add(settings.region());
        command.add("--username");
        command.add(settings.username());
        return command;
    }

    private static String execute(List<String> command, JdbcConnectionSettings settings) throws SQLException, IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new SQLException("AWS CLI token generation timed out after " + TIMEOUT.toSeconds() + " seconds.");
            }

            String stdout = read(process.getInputStream()).trim();
            String stderr = read(process.getErrorStream()).trim();
            if (process.exitValue() != 0) {
                throw new SQLException("AWS CLI failed while generating RDS IAM token. Exit code: "
                        + process.exitValue() + sanitizedError(stderr));
            }
            if (stdout.isEmpty()) {
                throw new SQLException("AWS CLI returned an empty RDS IAM token." + sanitizedError(stderr));
            }
            return stdout;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while generating AWS RDS IAM token.", e);
        }
    }

    private static boolean isDefaultAwsCliPath(String awsCliPath) {
        return awsCliPath == null || awsCliPath.isBlank() || "aws".equals(awsCliPath);
    }

    private static List<String> defaultAwsCliCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        if (isWindows()) {
            candidates.add("aws.exe");
            addWindowsAwsCliPath(candidates, System.getenv("ProgramFiles"));
            addWindowsAwsCliPath(candidates, System.getenv("ProgramFiles(x86)"));
            addWindowsAwsCliPath(candidates, System.getenv("LocalAppData"));
        } else {
            candidates.add("/opt/homebrew/bin/aws");
            candidates.add("/usr/local/bin/aws");
            candidates.add("/usr/bin/aws");
        }
        return new ArrayList<>(candidates);
    }

    private static void addWindowsAwsCliPath(Set<String> candidates, String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return;
        }
        candidates.add(basePath + "\\Amazon\\AWSCLIV2\\aws.exe");
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("win");
    }

    private static String read(java.io.InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append(System.lineSeparator());
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String sanitizedError(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "";
        }
        return " Error: " + stderr.replaceAll("(?i)(password|token|secret)=\\S+", "$1=<redacted>");
    }
}
