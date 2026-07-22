package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class AwsCliCommand {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private AwsCliCommand() {
    }

    static String execute(JdbcConnectionSettings settings, List<String> arguments, String operation) throws SQLException {
        IOException lastException = null;
        for (String executable : executableCandidates(settings.awsCliPath())) {
            List<String> command = command(executable, arguments);
            try {
                Process process = processBuilder(executable, arguments).start();
                StringBuilder stdoutBuffer = new StringBuilder();
                StringBuilder stderrBuffer = new StringBuilder();
                Thread stdoutThread = drainAsync(process.getInputStream(), stdoutBuffer, "aws-cli-stdout");
                Thread stderrThread = drainAsync(process.getErrorStream(), stderrBuffer, "aws-cli-stderr");
                boolean finished = process.waitFor(DEFAULT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    await(stdoutThread);
                    await(stderrThread);
                    throw new SQLException(operation + " timed out after " + DEFAULT_TIMEOUT.toSeconds() + " seconds.");
                }

                await(stdoutThread);
                await(stderrThread);
                String stdout = value(stdoutBuffer);
                String stderr = value(stderrBuffer);
                if (process.exitValue() != 0) {
                    throw new SQLException(operation + " failed. Exit code: " + process.exitValue() + sanitizedError(stderr));
                }
                return stdout;
            } catch (IOException e) {
                lastException = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while running " + operation + ".", e);
            }
        }

        throw new SQLException("Could not execute AWS CLI while running " + operation
                + ". Check AWS CLI Path: " + settings.awsCliPath(), lastException);
    }

    static Process start(JdbcConnectionSettings settings, List<String> arguments, String operation) throws SQLException {
        IOException lastException = null;
        for (String executable : executableCandidates(settings.awsCliPath())) {
            try {
                return processBuilder(executable, arguments).start();
            } catch (IOException e) {
                lastException = e;
            }
        }
        throw new SQLException("Could not execute AWS CLI while starting " + operation
                + ". Check AWS CLI Path: " + settings.awsCliPath(), lastException);
    }

    static List<String> commonArguments(JdbcConnectionSettings settings, String... serviceArguments) {
        List<String> arguments = new ArrayList<>();
        for (String argument : serviceArguments) {
            arguments.add(argument);
        }
        arguments.add("--profile");
        arguments.add(settings.profile());
        arguments.add("--region");
        arguments.add(settings.region());
        arguments.add("--no-cli-pager");
        return arguments;
    }

    static Thread drainAsync(InputStream input, StringBuilder target, String threadName) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (target) {
                        if (target.length() > 16_384) {
                            target.delete(0, target.length() - 8_192);
                        }
                        if (!target.isEmpty()) {
                            target.append(System.lineSeparator());
                        }
                        target.append(line);
                    }
                }
            } catch (IOException ignored) {
                // The stream is expected to close when the tunnel process is terminated.
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    static String sanitizedError(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "";
        }
        return " Error: " + stderr.replaceAll("(?i)(password|token|secret)=\\S+", "$1=<redacted>");
    }

    private static List<String> command(String executable, List<String> arguments) {
        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(executable);
        command.addAll(arguments);
        return command;
    }

    private static ProcessBuilder processBuilder(String executable, List<String> arguments) {
        ProcessBuilder processBuilder = new ProcessBuilder(command(executable, arguments));
        prependExecutablePaths(processBuilder.environment(), executable);
        return processBuilder;
    }

    private static void prependExecutablePaths(Map<String, String> environment, String executable) {
        Set<String> paths = new LinkedHashSet<>();
        File executableFile = new File(executable);
        addPath(paths, executableFile.getParent());

        if (isWindows()) {
            addWindowsSessionManagerPath(paths, System.getenv("ProgramFiles"));
            addWindowsSessionManagerPath(paths, System.getenv("ProgramFiles(x86)"));
            addWindowsSessionManagerPath(paths, System.getenv("LocalAppData"));
        } else {
            addPath(paths, "/usr/local/bin");
            addPath(paths, "/usr/local/sessionmanagerplugin/bin");
            addPath(paths, "/opt/homebrew/bin");
            addPath(paths, "/usr/bin");
            addPath(paths, "/bin");
        }

        String pathKey = environment.keySet().stream()
                .filter(key -> "PATH".equalsIgnoreCase(key))
                .findFirst()
                .orElse("PATH");
        String inheritedPath = environment.get(pathKey);
        if (inheritedPath != null && !inheritedPath.isBlank()) {
            for (String path : inheritedPath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                addPath(paths, path);
            }
        }
        environment.put(pathKey, String.join(File.pathSeparator, paths));
    }

    private static void addWindowsSessionManagerPath(Set<String> paths, String basePath) {
        if (basePath != null && !basePath.isBlank()) {
            addPath(paths, basePath + "\\Amazon\\SessionManagerPlugin\\bin");
        }
    }

    private static void addPath(Set<String> paths, String path) {
        if (path != null && !path.isBlank()) {
            paths.add(path.trim());
        }
    }

    private static List<String> executableCandidates(String configuredPath) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(configuredPath == null || configuredPath.isBlank() ? "aws" : configuredPath.trim());
        if (configuredPath != null && !configuredPath.isBlank() && !"aws".equals(configuredPath.trim())) {
            return new ArrayList<>(candidates);
        }

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
        if (basePath != null && !basePath.isBlank()) {
            candidates.add(basePath + "\\Amazon\\AWSCLIV2\\aws.exe");
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void await(Thread thread) throws InterruptedException {
        thread.join(2_000);
    }

    private static String value(StringBuilder buffer) {
        synchronized (buffer) {
            return buffer.toString().trim();
        }
    }
}
