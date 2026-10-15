package com.carrental.support;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Prints one line when each test starts and one when it finishes.
 * Registered through META-INF/services, so it runs for every test with no per-class setup.
 */
public class TestProgressListener implements TestExecutionListener {

    @Override
    public void executionStarted(TestIdentifier test) {
        if (test.isTest()) {
            System.out.println("[TEST] RUNNING  " + name(test));
        }
    }

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult result) {
        if (!test.isTest()) {
            return;
        }
        String outcome = switch (result.getStatus()) {
            case SUCCESSFUL -> "PASSED ";
            case FAILED -> "FAILED ";
            case ABORTED -> "ABORTED";
        };
        String detail = result.getThrowable().map(t -> "  " + t.getClass().getSimpleName() + ": " + t.getMessage())
                .orElse("");
        System.out.println("[TEST] " + outcome + "  " + name(test) + detail);
    }

    @Override
    public void executionSkipped(TestIdentifier test, String reason) {
        if (test.isTest()) {
            System.out.println("[TEST] SKIPPED  " + name(test) + "  " + reason);
        }
    }

    private static String name(TestIdentifier test) {
        return test.getSource()
                .filter(MethodSource.class::isInstance)
                .map(s -> {
                    MethodSource m = (MethodSource) s;
                    return simpleName(m.getClassName()) + "." + m.getMethodName();
                })
                .or(() -> test.getSource().filter(ClassSource.class::isInstance)
                        .map(s -> simpleName(((ClassSource) s).getClassName())))
                .orElse(test.getDisplayName());
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
