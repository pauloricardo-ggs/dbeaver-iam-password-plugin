package dev.pauloricardo.dbeaver.aws.rds.iam.driver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

final class TunneledConnection {
    private TunneledConnection() {
    }

    static Connection wrap(Connection delegate, SsmTunnelManager.TunnelLease tunnel) {
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, arguments) -> invoke(delegate, tunnel, closed, proxy, method, arguments)
        );
    }

    private static Object invoke(
            Connection delegate,
            SsmTunnelManager.TunnelLease tunnel,
            AtomicBoolean closed,
            Object proxy,
            Method method,
            Object[] arguments
    ) throws Throwable {
        String name = method.getName();
        if (("close".equals(name) && method.getParameterCount() == 0)
                || ("abort".equals(name) && method.getParameterCount() == 1)) {
            if (closed.compareAndSet(false, true)) {
                try {
                    if ("abort".equals(name)) {
                        delegate.abort((java.util.concurrent.Executor) arguments[0]);
                    } else {
                        delegate.close();
                    }
                } finally {
                    tunnel.close();
                }
            }
            return null;
        }
        if ("isClosed".equals(name) && method.getParameterCount() == 0) {
            return closed.get() || delegate.isClosed();
        }
        if ("isWrapperFor".equals(name) && method.getParameterCount() == 1) {
            Class<?> type = (Class<?>) arguments[0];
            return type.isInstance(proxy) || type.isInstance(delegate) || delegate.isWrapperFor(type);
        }
        if ("unwrap".equals(name) && method.getParameterCount() == 1) {
            Class<?> type = (Class<?>) arguments[0];
            if (type.isInstance(proxy)) {
                return proxy;
            }
            if (type.isInstance(delegate)) {
                return delegate;
            }
            return delegate.unwrap(type);
        }
        try {
            return method.invoke(delegate, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } catch (IllegalAccessException e) {
            throw new SQLException("Could not delegate JDBC connection method " + name + ".", e);
        }
    }
}
