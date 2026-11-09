package com.chayitong.common.aspect;

import com.chayitong.common.annotation.RequirePermission;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequirePermissionAspect {

	@Around("@annotation(requirePermission)")
	public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		Set<String> authorities = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());
		boolean allowed = Arrays.stream(requirePermission.value()).allMatch(authorities::contains);
		if (!allowed) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		return joinPoint.proceed();
	}
}
