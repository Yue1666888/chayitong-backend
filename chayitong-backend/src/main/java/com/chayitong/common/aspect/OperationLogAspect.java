package com.chayitong.common.aspect;

import com.chayitong.common.annotation.OperationLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class OperationLogAspect {

	private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

	@Around("@annotation(operationLog)")
	public Object recordOperation(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
		long startTime = System.nanoTime();
		try {
			Object result = joinPoint.proceed();
			log.info("操作日志: action={}, method={}, result=success, durationMs={}",
					operationLog.value(), joinPoint.getSignature().toShortString(), elapsedMillis(startTime));
			return result;
		} catch (Throwable exception) {
			log.warn("操作日志: action={}, method={}, result=failure, durationMs={}, error={}",
					operationLog.value(), joinPoint.getSignature().toShortString(), elapsedMillis(startTime),
					exception.getMessage());
			throw exception;
		}
	}

	private long elapsedMillis(long startTime) {
		return (System.nanoTime() - startTime) / 1_000_000;
	}
}
