package br.ufsc.inf.lapesd.sddms.endpoint;

import java.io.StringReader;
import java.io.StringWriter;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.DataManager;
import br.ufsc.inf.lapesd.sddms.OntologyManager;

@Path("ontology")
@Component
public class OntoloyEndpoint {

    @Context
    private UriInfo uriInfo;

    @Autowired
    private OntologyManager ontologyManager;

    @Autowired
    private DataManager dataManager;

    @GET
    @Produces({ "application/n-quads", "application/ld+json", "application/rdf+thrift", "application/x-turtle", "application/x-trig", "application/rdf+xml", "text/turtle", "application/trix", "application/turtle", "text/n-quads", "application/rdf+json", "application/trix+xml", "application/trig", "text/trig", "application/n-triples", "text/nquads", "text/plain" })
    public Response loadOntology() {
        Model ontologyModel = ontologyManager.loadOntology();

        StringWriter out = new StringWriter();
        ontologyModel.write(out, Lang.TURTLE.getName());
        String modelString = out.toString();

        String path = uriInfo.getBaseUri() + "resource?uri=";
        String resourcePrefix = this.dataManager.getResourcePrefix();
        String modelStringReplaced = modelString.replace(resourcePrefix, path);

        ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        ontologyModel.read(new StringReader(modelStringReplaced), null, Lang.TURTLE.getName());

        return Response.ok(ontologyModel).build();
    }

    @POST
    @Consumes({ "application/n-quads", "application/ld+json", "application/rdf+thrift", "application/x-turtle", "application/x-trig", "application/rdf+xml", "text/turtle", "application/trix", "application/turtle", "text/n-quads", "application/rdf+json", "application/trix+xml", "application/trig", "text/trig", "application/n-triples", "text/nquads", "text/plain" })
    public Response saveOntology(Model model) {

        StringWriter out = new StringWriter();
        model.write(out, Lang.TURTLE.getName());
        String modelString = out.toString();

        String pathToReplace = uriInfo.getBaseUri() + "resource?uri=";
        String modelStringReplaced = modelString.replace(pathToReplace, this.dataManager.getResourcePrefix());

        InfModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        ontologyModel.read(new StringReader(modelStringReplaced), null, Lang.TURTLE.getName());

        ontologyManager.saveOntology(ontologyModel);
        return Response.ok().build();
    }

}
