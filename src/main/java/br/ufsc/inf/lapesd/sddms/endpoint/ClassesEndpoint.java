package br.ufsc.inf.lapesd.sddms.endpoint;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.DataManager;

@Path("classes")
@Component
public class ClassesEndpoint {

    @Autowired
    private DataManager dataManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllManagedClasses() {
        List<String> allManagedSemanticClasses = dataManager.getAllManagedSemanticClasses();
        return Response.ok(allManagedSemanticClasses).build();
    }

}
