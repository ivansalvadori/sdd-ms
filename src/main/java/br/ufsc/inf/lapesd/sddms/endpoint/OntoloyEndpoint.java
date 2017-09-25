package br.ufsc.inf.lapesd.sddms.endpoint;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.OntologyManager;

@Path("ontology")
@Component
public class OntoloyEndpoint {

    @Autowired
    private OntologyManager ontologyManager;

    @GET
    @Produces({ "application/n-quads", "application/ld+json", "application/rdf+thrift", "application/x-turtle", "application/x-trig", "application/rdf+xml", "text/turtle", "application/trix", "application/turtle", "text/n-quads", "application/rdf+json", "application/trix+xml", "application/trig", "text/trig", "application/n-triples", "text/nquads", "text/plain" })
    public Response loadOntology() {
        Model ontologyModel = ontologyManager.loadOntology();
        return Response.ok(ontologyModel).build();
    }

    @POST
    @Consumes({ "application/n-quads", "application/ld+json", "application/rdf+thrift", "application/x-turtle", "application/x-trig", "application/rdf+xml", "text/turtle", "application/trix", "application/turtle", "text/n-quads", "application/rdf+json", "application/trix+xml", "application/trig", "text/trig", "application/n-triples", "text/nquads", "text/plain" })
    public Response saveOntology(Model model) {
        ontologyManager.saveOntology(model);
        return Response.ok().build();
    }

}
