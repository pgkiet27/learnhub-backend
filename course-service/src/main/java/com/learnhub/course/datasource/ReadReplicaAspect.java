package com.learnhub.course.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Runs outside the transaction interceptor so the flag is set before the first statement picks a connection
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReadReplicaAspect {

    @Around("@annotation(com.learnhub.course.datasource.ReadFromReplica)")
    public Object routeToReplica(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean outer = ReplicaContext.isActive();
        ReplicaContext.set(true);
        try {
            return joinPoint.proceed();
        } finally {
            ReplicaContext.set(outer);
        }
    }
}
