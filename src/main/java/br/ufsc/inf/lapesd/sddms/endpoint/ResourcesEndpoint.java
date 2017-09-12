package br.ufsc.inf.lapesd.sddms.endpoint;

import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.HashMap;
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

import org.apache.http.client.utils.URIBuilder;
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
    @Path("/resources")
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryResources(@QueryParam("uriClass") String uriClass, @QueryParam("sddms:pageId") String pageId) {
        Map<String, String> propertyValues = new HashMap<>();
        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        Set<String> keySet = queryParameters.keySet();
        for (String prop : keySet) {
            propertyValues.put(prop, queryParameters.getFirst(prop));
        }
        propertyValues.remove("uriClass");
        Model queryTDB = dataManager.queryTDB(uriClass, propertyValues);
        String requestedUri = uriInfo.getRequestUri().toString();
        Resource renamedResource = ResourceUtils.renameResource(queryTDB.getResource("http://sddms.com.br/ontology/ResourceList"), requestedUri);

        StringWriter out = new StringWriter();
        renamedResource.getModel().write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();
        String resourceStringReplaced = resourceString.replaceAll("http://sddms-resource/", uriInfo.getBaseUri() + "resource?uri=");

        String requestedUriPagination = createLinkForPagination(pageId, queryParameters, keySet);

        String resourceStringReplacedHydraNextIfExists = resourceStringReplaced.replaceAll("sddms:pageId", requestedUriPagination + "&sddms:pageId");

        return Response.ok(resourceStringReplacedHydraNextIfExists).build();
    }

    private String createLinkForPagination(String pageId, MultivaluedMap<String, String> queryParameters, Set<String> keySet) {
        URIBuilder uriBuilder = new URIBuilder(uriInfo.getAbsolutePath());
        for (String prop : keySet) {
            if (!prop.equalsIgnoreCase("sddms:pageId")) {
                uriBuilder.addParameter(prop, queryParameters.get(prop).get(0));
            }
        }
        try {
            uriBuilder.build();
        } catch (URISyntaxException e1) {
            e1.printStackTrace();
        }
        return uriBuilder.toString();
    }

    @GET
    @Path("/resource")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllResources(@QueryParam("uri") String uri) {
        Model resource = dataManager.getResource("http://sddms-resource/" + uri);

        Resource renamedResource = ResourceUtils.renameResource(resource.getResource(uri), uriInfo.getRequestUri().toString());
        StringWriter out = new StringWriter();
        renamedResource.getModel().write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();
        String resourceStringReplaced = resourceString.replaceAll("http://sddms-resource/", uriInfo.getAbsolutePath() + "?uri=");

        return Response.ok(resourceStringReplaced).build();
    }
}
