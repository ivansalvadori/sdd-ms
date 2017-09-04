package br.ufsc.inf.lapesd.sddms.endpoint;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.DataManager;

@Path("reader")
@Component
public class ReaderEndpoint {

    @Autowired
    private DataManager dataManager;

    @GET
    @Path("read")
    @Produces(MediaType.APPLICATION_JSON)
    public Response read() {
        dataManager.readAndStore();
        return Response.ok("Reading").build();
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReadingStatus() {
        return Response.ok(dataManager.getReadingStatus()).build();
    }

    @GET
    @Path("reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetDataset() {
        dataManager.resetDataset();
        return Response.ok("Removing all data").build();
    }

}
