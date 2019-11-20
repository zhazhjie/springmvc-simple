package com.zzj.core.dispatch;

import java.lang.annotation.Annotation;

@FunctionalInterface
public interface CheckAnnotation<T extends Annotation> {
    T run();
}
