package br.ufsc.inf.lapesd.sddms.config;


import java.io.IOException;
import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

@Provider
public class CorsInterceptor implements ContainerResponseFilter {

    private final Integer corsPreflightMaxAgeInSeconds = 30 * 60;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        responseContext.getHeaders().add("Access-Control-Allow-Origin", requestContext.getHeaderString("origin"));
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");

        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        List<String> allowedHeaders = requestContext.getHeaders().get("Access-Control-Request-Headers");
        if (allowedHeaders != null) {
            for (String allowedHeader : allowedHeaders) {
                responseContext.getHeaders().add("Access-Control-Allow-Headers", allowedHeader);
            }
        }

        responseContext.getHeaders().add("Access-Control-Max-Age", this.corsPreflightMaxAgeInSeconds);
    }
}