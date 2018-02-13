package br.ufsc.inf.lapesd.sddms.endpoint;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.stereotype.Component;

@Path("mapping")
@Component
public class MappingEndpoint {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadMapping() {
        String configString = null;
        try {
            configString = new String(Files.readAllBytes(Paths.get("mapping.jsonld")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Response.ok(configString).build();
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response saveMapping(String config) {
        try (FileWriter fostream = new FileWriter("mapping.jsonld", false);) {
            fostream.write(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.ok().build();
    }

}
