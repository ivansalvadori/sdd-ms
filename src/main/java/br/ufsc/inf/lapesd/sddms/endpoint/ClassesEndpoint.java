package br.ufsc.inf.lapesd.sddms.endpoint;

import java.io.StringWriter;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.util.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.DataManager;

@Path("classes")
@Component
public class ClassesEndpoint {

    @Autowired
    private DataManager dataManager;

    @Context
    private UriInfo uriInfo;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllManagedClasses() {
        List<String> allManagedSemanticClasses = dataManager.getAllManagedSemanticClasses();

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

}
