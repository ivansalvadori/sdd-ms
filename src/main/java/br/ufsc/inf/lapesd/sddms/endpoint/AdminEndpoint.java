package br.ufsc.inf.lapesd.sddms.endpoint;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.springframework.stereotype.Component;

@Path("admin")
@Component
public class AdminEndpoint {

    @GET
    @Path("stop")
    public Response stopService() {
        System.exit(0);
        return Response.noContent().build();
    }

}
