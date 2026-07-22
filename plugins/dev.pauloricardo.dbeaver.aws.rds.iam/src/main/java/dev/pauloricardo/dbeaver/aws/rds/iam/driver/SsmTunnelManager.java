package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SsmTunnelManager {
    private static final Duration START_TIMEOUT = Duration.ofSeconds(25);
    private static final Object LOCK = new Object();
    private static final Map<TunnelKey, ManagedTunnel> TUNNELS = new HashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(SsmTunnelManager::closeAll, "aws-rds-ssm-shutdown"));
    }

    TunnelLease acquire(
            JdbcConnectionSettings settings,
            String instanceId,
            RdsTagResolver.RdsEndpoint endpoint
    ) throws SQLException {
        TunnelKey key = new TunnelKey(
                settings.awsCliPath(),
                settings.profile(),
                settings.region(),
                instanceId,
                endpoint.hostname(),
                endpoint.port(),
                settings.localPort()
        );

        synchronized (LOCK) {
            ManagedTunnel existing = TUNNELS.get(key);
            if (existing != null && existing.isAlive()) {
                existing.references++;
                return new TunnelLease(key, existing);
            }
            if (existing != null) {
                TUNNELS.remove(key);
                existing.close();
            }

            ensureLocalPortAvailable(settings.localPort());
            ManagedTunnel created = start(settings, key);
            created.references = 1;
            TUNNELS.put(key, created);
            return new TunnelLease(key, created);
        }
    }

    private static ManagedTunnel start(JdbcConnectionSettings settings, TunnelKey key) throws SQLException {
        String parameters = "{\"host\":[\"" + AwsCliCommand.jsonEscape(key.rdsHostname()) + "\"],"
                + "\"portNumber\":[\"" + key.rdsPort() + "\"],"
                + "\"localPortNumber\":[\"" + key.localPort() + "\"]}";
        List<String> arguments = AwsCliCommand.commonArguments(
                settings,
                "ssm", "start-session",
                "--target", key.instanceId(),
                "--document-name", "AWS-StartPortForwardingSessionToRemoteHost",
                "--parameters", parameters
        );
        Process process = AwsCliCommand.start(settings, arguments, "SSM port forwarding session");
        ManagedTunnel tunnel = new ManagedTunnel(process);
        tunnel.stdoutThread = AwsCliCommand.drainAsync(
                process.getInputStream(), tunnel.stdout, "aws-rds-ssm-stdout-" + key.localPort());
        tunnel.stderrThread = AwsCliCommand.drainAsync(
                process.getErrorStream(), tunnel.stderr, "aws-rds-ssm-stderr-" + key.localPort());

        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            tunnel.captureDescendants();
            if (!process.isAlive()) {
                tunnel.close();
                throw new SQLException("SSM port forwarding session exited before local port "
                        + key.localPort() + " became available." + tunnel.diagnostics());
            }
            if (canConnect(key.localPort())) {
                if (settings.debug()) {
                    System.err.println("[dbeaver-aws-rds-iam-auth] SSM tunnel ready on 127.0.0.1:"
                            + key.localPort() + " via " + key.instanceId() + " to "
                            + key.rdsHostname() + ":" + key.rdsPort());
                }
                return tunnel;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                tunnel.close();
                throw new SQLException("Interrupted while waiting for the SSM tunnel to start.", e);
            }
        }

        tunnel.close();
        throw new SQLException("SSM port forwarding session did not make local port " + key.localPort()
                + " available within " + START_TIMEOUT.toSeconds() + " seconds." + tunnel.diagnostics());
    }

    private static void ensureLocalPortAvailable(int localPort) throws SQLException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("127.0.0.1", localPort));
        } catch (IOException e) {
            throw new SQLException("Local Port " + localPort
                    + " is already in use. Choose another port or close the process using it.", e);
        }
    }

    private static boolean canConnect(int localPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", localPort), 200);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void release(TunnelKey key, ManagedTunnel tunnel) {
        synchronized (LOCK) {
            ManagedTunnel registered = TUNNELS.get(key);
            if (registered != tunnel) {
                tunnel.close();
                return;
            }
            tunnel.references--;
            if (tunnel.references <= 0) {
                TUNNELS.remove(key);
                tunnel.close();
            }
        }
    }

    private static void closeAll() {
        synchronized (LOCK) {
            for (ManagedTunnel tunnel : TUNNELS.values()) {
                tunnel.close();
            }
            TUNNELS.clear();
        }
    }

    static final class TunnelLease implements AutoCloseable {
        private final TunnelKey key;
        private final ManagedTunnel tunnel;
        private final AtomicBoolean closed = new AtomicBoolean();

        private TunnelLease(TunnelKey key, ManagedTunnel tunnel) {
            this.key = key;
            this.tunnel = tunnel;
        }

        int localPort() {
            return key.localPort();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(key, tunnel);
            }
        }
    }

    private record TunnelKey(
            String awsCliPath,
            String profile,
            String region,
            String instanceId,
            String rdsHostname,
            int rdsPort,
            int localPort
    ) {
    }

    private static final class ManagedTunnel {
        private final Process process;
        private final Map<Long, ProcessHandle> descendants = new HashMap<>();
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();
        private Thread stdoutThread;
        private Thread stderrThread;
        private int references;

        private ManagedTunnel(Process process) {
            this.process = process;
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private String diagnostics() {
            String error;
            synchronized (stderr) {
                error = stderr.toString().trim();
            }
            if (error.isBlank()) {
                synchronized (stdout) {
                    error = stdout.toString().trim();
                }
            }
            return AwsCliCommand.sanitizedError(error);
        }

        private void awaitDrainers() {
            await(stdoutThread);
            await(stderrThread);
        }

        private void captureDescendants() {
            process.descendants().forEach(handle -> descendants.put(handle.pid(), handle));
        }

        private void close() {
            captureDescendants();
            List<ProcessHandle> childProcesses = new ArrayList<>(descendants.values());
            for (ProcessHandle childProcess : childProcesses) {
                if (childProcess.isAlive()) {
                    childProcess.destroy();
                }
            }
            if (process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
            for (ProcessHandle childProcess : childProcesses) {
                if (childProcess.isAlive()) {
                    childProcess.destroyForcibly();
                }
            }
            awaitDrainers();
        }

        private static void await(Thread thread) {
            if (thread == null) {
                return;
            }
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
