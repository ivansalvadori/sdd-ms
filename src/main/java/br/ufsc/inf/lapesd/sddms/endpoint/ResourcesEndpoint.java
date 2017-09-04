package br.ufsc.inf.lapesd.sddms.endpoint;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.util.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.DataManager;

@Path("/")
@Component
public class ResourcesEndpoint {

    @Context
    private UriInfo uriInfo;

    @Autowired
    private DataManager dataManager;

    @GET
    @Path("/classe")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllClasses(@QueryParam("uri") String uri) {
        List<String> uris = dataManager.listAllResourceUris(uri);
        return Response.ok(uris).build();
    }

    @GET
    @Path("/resources")
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryResources(@QueryParam("uriClass") String uriClass) {
        Map<String, String> propertyValues = new HashMap<>();
        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        Set<String> keySet = queryParameters.keySet();
        for (String prop : keySet) {
            propertyValues.put(prop, queryParameters.getFirst(prop));
        }
        propertyValues.remove("uriClass");
        Model queryTDB = dataManager.queryTDB(uriClass, propertyValues);

        StringWriter out = new StringWriter();
        queryTDB.write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();
        String resourceStringReplaced = resourceString.replaceAll("sddms-resource:", uriInfo.getBaseUri() + "resource?uri=");
        return Response.ok(resourceStringReplaced).build();
    }

    @GET
    @Path("/resource")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllResources(@QueryParam("uri") String uri) {
        Model resource = dataManager.getResource("sddms-resource:" + uri);
        Resource renamedResource = ResourceUtils.renameResource(resource.getResource(uri), uriInfo.getRequestUri().toString());
        StringWriter out = new StringWriter();
        renamedResource.getModel().write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();
        String resourceStringReplaced = resourceString.replaceAll("sddms-resource:", uriInfo.getAbsolutePath() + "?uri=");
        return Response.ok(resourceStringReplaced).build();
    }
}
