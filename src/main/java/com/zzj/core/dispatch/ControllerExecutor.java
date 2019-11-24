package com.zzj.core.dispatch;

@FunctionalInterface
public interface ControllerExecutor {
    void run() throws Exception;
}
