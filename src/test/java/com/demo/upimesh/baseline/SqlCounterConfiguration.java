package com.demo.upimesh.baseline;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Test-only counter at the JDBC boundary, including JdbcTemplate and Hibernate. */
@TestConfiguration(proxyBeanMethods = false)
public class SqlCounterConfiguration {
    static final AtomicLong EXECUTIONS = new AtomicLong();

    @Bean
    static BeanPostProcessor countSqlExecutions() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (!(bean instanceof DataSource)) return bean;
                return proxy(DataSource.class, bean, (target, method, args) -> {
                    Object result = invoke(target, method, args);
                    return result instanceof Connection connection ? connection(connection) : result;
                });
            }
        };
    }

    private static Object connection(Connection connection) {
        return proxy(Connection.class, connection, (target, method, args) -> {
            Object result = invoke(target, method, args);
            if (!(result instanceof Statement statement)) return result;
            Class<?> type = statement instanceof CallableStatement ? CallableStatement.class
                    : statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return proxy(type, statement, (stmt, operation, parameters) -> {
                // Count JDBC execution calls, including failed attempts. A batch is one call.
                if (operation.getName().startsWith("execute")) EXECUTIONS.incrementAndGet();
                return invoke(stmt, operation, parameters);
            });
        });
    }

    private static Object proxy(Class<?> type, Object target, Invocation action) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> action.call(target, method, args));
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException ex) { throw ex.getCause(); }
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(Object target, Method method, Object[] args) throws Throwable;
    }
}
