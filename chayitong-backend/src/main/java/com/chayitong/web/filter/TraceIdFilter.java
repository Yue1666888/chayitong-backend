package com.chayitong.web.filter;

import com.chayitong.common.api.TraceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String traceId = UUID.randomUUID().toString().replace("-", "");
		MDC.put(TraceId.HEADER_NAME, traceId);
		response.setHeader(TraceId.HEADER_NAME, traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(TraceId.HEADER_NAME);
		}
	}
}
