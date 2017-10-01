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
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.http.client.utils.URIBuilder;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.DataManager;
import br.ufsc.inf.lapesd.sddms.OntologyManager;

@Path("/")
@Component
public class ResourcesEndpoint {

    @Context
    private UriInfo uriInfo;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private OntologyManager ontologyManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllManagedClasses() {
        List<String> allManagedSemanticClasses = dataManager.getAllManagedSemanticClasses();

        InfModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        Resource apiDocType = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "ApiDocumentation");
        Resource hydraClass = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "Class");
        Resource hydraDoc = resourceModel.createResource("http://sddms.com.br/ontology/" + "ResourceList", apiDocType);

        String baseUri = uriInfo.getBaseUri().toString();

        // OntModel ontologyInfModel =
        // ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF,
        // this.ontologyManager.createOntologyInfModel());

        for (String uri : allManagedSemanticClasses) {
            Resource managedClasse = resourceModel.createResource(uri, hydraClass);
            managedClasse.addProperty(resourceModel.createProperty("http://www.w3.org/2000/01/rdf-schema#seeAlso"), baseUri + "resources?uriClass=" + uri);

            // boolean needInference = this.needInference(uri, ontologyInfModel);
            // managedClasse.addProperty(resourceModel.createProperty("http://sddms.com.br/ontology/inferenceRequired"),
            // String.valueOf(needInference));

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
    @Produces({ "application/n-quads", "application/ld+json", "application/rdf+thrift", "application/x-turtle", "application/x-trig", "application/rdf+xml", "text/turtle", "application/trix", "application/turtle", "text/n-quads", "application/rdf+json", "application/trix+xml", "application/trig", "text/trig", "application/n-triples", "text/nquads", "text/plain" })
    public Response queryResources(@QueryParam("uriClass") String uriClass, @QueryParam("sddms:pageId") String pageId) {
        Map<String, String> propertyValues = new HashMap<>();
        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        Set<String> keySet = queryParameters.keySet();
        for (String prop : keySet) {
            String firstPropertyValue = queryParameters.getFirst(prop);
            String pathToReplace = uriInfo.getBaseUri() + "resource?uri=";
            String firstPropertyValueReplaced = firstPropertyValue.replace(pathToReplace, this.dataManager.getResourcePrefix());
            propertyValues.put(prop, firstPropertyValueReplaced);
        }
        propertyValues.remove("uriClass");
        Model queryTDB = dataManager.queryTDB(uriClass, propertyValues);
        String requestedUri = uriInfo.getRequestUri().toString();
        Resource renamedResource = ResourceUtils.renameResource(queryTDB.getResource("http://sddms.com.br/ontology/ResourceList"), requestedUri);

        StringWriter out = new StringWriter();
        renamedResource.getModel().write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();
        String resourceStringReplaced = resourceString.replaceAll(this.dataManager.getResourcePrefix(), uriInfo.getBaseUri() + "resource?uri=");

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
    @Path("/resource")
    @Produces({ "application/n-quads", "application/ld+json", "application/rdf+thrift", "application/x-turtle", "application/x-trig", "application/rdf+xml", "text/turtle", "application/trix", "application/turtle", "text/n-quads", "application/rdf+json", "application/trix+xml", "application/trig", "text/trig", "application/n-triples", "text/nquads", "text/plain" })
    public Response listAllResources(@QueryParam("uri") String uri) {
        Model resource = dataManager.getResource(this.dataManager.getResourcePrefix() + uri);
        Resource renamedResource = ResourceUtils.renameResource(resource.getResource(uri), uriInfo.getRequestUri().toString());
        StringWriter out = new StringWriter();
        renamedResource.getModel().write(out, Lang.JSONLD.getName());
        String resourceString = out.toString();
        String resourceStringReplaced = resourceString.replaceAll(this.dataManager.getResourcePrefix(), uriInfo.getAbsolutePath() + "?uri=");

        Model resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        InputStream resourceStream = new ByteArrayInputStream(resourceStringReplaced.getBytes());

        RDFDataMgr.read(resourceModel, resourceStream, Lang.JSONLD);

        return Response.ok(resourceModel).build();
    }

    private boolean needInference(String rdfType, OntModel model) {
        boolean need = false;

        OntClass ontClass = model.getOntClass(rdfType);

        ExtendedIterator<OntClass> subClasses = ontClass.listSubClasses();
        while (subClasses.hasNext()) {
            OntClass subClass = subClasses.next();
            if (subClass.isRestriction()) {
                need = true;
            }
        }

        ExtendedIterator<OntClass> eqvClasses = ontClass.listEquivalentClasses();
        while (eqvClasses.hasNext()) {
            OntClass eqvClass = eqvClasses.next();
            if (eqvClass.isRestriction() || eqvClass.isIntersectionClass()) {
                return true;
            }
        }
        return need;
    }

}
