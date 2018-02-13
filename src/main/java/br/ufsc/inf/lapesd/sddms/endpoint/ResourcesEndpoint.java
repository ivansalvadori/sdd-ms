package br.ufsc.inf.lapesd.sddms.endpoint;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.http.client.utils.URIBuilder;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.OntologyManager;
import br.ufsc.inf.lapesd.sddms.database.DataBase;

@Path("/")
@Component
public class ResourcesEndpoint {

    @Context
    private UriInfo uriInfo;

    @Autowired
    private OntologyManager ontologyManager;

    @Autowired
    private DataBase dataBase;

    @Value("${config.managedUri}")
    private String managedUri = "http://example.com";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllManagedClasses() {
        List<String> allManagedSemanticClasses = ontologyManager.listAllClasses();

        InfModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        Resource apiDocType = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "ApiDocumentation");
        Resource hydraClass = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "Class");
        Resource hydraDoc = resourceModel.createResource("http://sddms.com.br/ontology/" + "ResourceList", apiDocType);

        String baseUri = uriInfo.getBaseUri().toString();

        for (String uri : allManagedSemanticClasses) {
            Resource managedClasse = resourceModel.createResource(uri, hydraClass);
            managedClasse.addProperty(resourceModel.createProperty("http://www.w3.org/2000/01/rdf-schema#seeAlso"), baseUri + "resources?uriClass=" + uri);

            hydraDoc.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "supportedClass"), managedClasse);
        }

        String requestedUri = uriInfo.getRequestUri().toString();
        Resource renamedResource = ResourceUtils.renameResource(hydraDoc, requestedUri);

        StringWriter out = new StringWriter();
        renamedResource.getModel().write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();

        return Response.ok(resourceString).build();
    }

    @GET
    @Path("/resources")
    @Produces({ "application/n-quads", "application/ld+json;qs=1", "application/rdf+thrift", "application/x-turtle", "application/x-trig", "application/rdf+xml", "text/turtle", "application/trix", "application/turtle", "text/n-quads", "application/rdf+json", "application/trix+xml", "application/trig", "text/trig", "application/n-triples", "text/nquads", "text/plain" })
    public Response queryResources(@QueryParam("uriClass") String uriClass, @QueryParam("sddms:pageId") String pageId) {
        Map<String, String> propertyValues = new HashMap<>();
        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        Set<String> keySet = queryParameters.keySet();
        for (String prop : keySet) {
            String firstPropertyValue = queryParameters.getFirst(prop);
            String pathToReplace = uriInfo.getBaseUri() + "resource/";
            String firstPropertyValueReplaced = firstPropertyValue.replace(pathToReplace, managedUri);
            propertyValues.put(prop, firstPropertyValueReplaced);
        }
        propertyValues.remove("uriClass");
        Model queryTDB = dataBase.queryTDB(uriClass, propertyValues);
        String requestedUri = uriInfo.getRequestUri().toString();
        Resource renamedResource = ResourceUtils.renameResource(queryTDB.getResource("http://sddms.com.br/ontology/ResourceList"), requestedUri);

        StringWriter out = new StringWriter();
        renamedResource.getModel().write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();
        String resourceStringReplaced = resourceString.replaceAll(managedUri, uriInfo.getBaseUri() + "resource/");

        String requestedUriPagination = createLinkForPagination(pageId, queryParameters, keySet);

        String resourceStringReplacedHydraNextIfExists = resourceStringReplaced.replaceAll("sddms:pageId", requestedUriPagination + "&sddms:pageId");

        Model resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        InputStream resourceStream = new ByteArrayInputStream(resourceStringReplacedHydraNextIfExists.getBytes());

        RDFDataMgr.read(resourceModel, resourceStream, Lang.JSONLD);

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
    @Path("/resource/{uri}")
    @Produces({ "application/n-quads;qs=0.8", "application/ld+json;qs=1", "application/rdf+thrift", "application/x-turtle", "application/x-trig", "application/rdf+xml", "text/turtle", "application/trix", "application/turtle", "text/n-quads", "application/rdf+json", "application/trix+xml", "application/trig", "text/trig", "application/n-triples", "text/nquads", "text/plain" })
    public Response listAllResources(@PathParam("uri") String uri) {
        Model resource = dataBase.load(managedUri + uri);
        Resource renamedResource = ResourceUtils.renameResource(resource.getResource(uri), uriInfo.getRequestUri().toString());
        StringWriter out = new StringWriter();
        renamedResource.getModel().write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();
        String resourceStringReplaced = resourceString.replaceAll(managedUri, uriInfo.getBaseUri() + "resource/");

        Model resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        InputStream resourceStream = new ByteArrayInputStream(resourceStringReplaced.getBytes());

        RDFDataMgr.read(resourceModel, resourceStream, Lang.JSONLD);

        return Response.ok(resourceModel).build();
    }
}
