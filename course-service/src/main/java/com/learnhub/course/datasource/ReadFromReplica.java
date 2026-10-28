package com.learnhub.course.datasource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sends the method's queries to the read replica when one is configured (app.datasource.read.url).
 * Only for public reads that tolerate replication lag; reads that follow a write stay on the primary.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadFromReplica {
}
