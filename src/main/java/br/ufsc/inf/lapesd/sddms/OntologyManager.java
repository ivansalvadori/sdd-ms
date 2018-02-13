package br.ufsc.inf.lapesd.sddms;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OntologyManager {

    @Value("${config.ontologyFile}")
    private String ontologyFile = "ontology.owl";

    @Value("${config.ontologyFormat}")
    private String ontologyFormat = "N3";

    @Value("${config.managedUri}")
    private String managedUri = "http://example.com";

    private OntModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);

    public Model loadOntology() {
        return createOntologyModel();
    }

    @PostConstruct
    public void init() {
        this.ontologyModel = createOntologyModel();
    }

    public void saveOntology(Model model) {
        try (FileWriter fostream = new FileWriter(ontologyFile, false);) {
            model.write(fostream, ontologyFormat);
            this.ontologyModel = createOntologyModel();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected OntModel createOntologyModel() {
        String ontologyString = null;
        try {
            ontologyString = new String(Files.readAllBytes(Paths.get(ontologyFile)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        model.read(new StringReader(ontologyString), null, ontologyFormat);

        NodeIterator listOfImports = model.listObjectsOfProperty(OWL.imports);
        while (listOfImports.hasNext()) {
            RDFNode resourceToImport = listOfImports.next();
            if (!resourceToImport.toString().contains(managedUri)) {
                RDFDataMgr.read(model, resourceToImport.toString());
            }
        }

        return model;
    }

    public List<String> listAllClasses() {
        List<String> semanticClasses = new ArrayList<>();
        List<OntClass> ontoClasses = this.ontologyModel.listClasses().toList();
        for (OntClass ontClass : ontoClasses) {
            String uri = ontClass.getURI();
            if (uri != null && !uri.startsWith("http://www.w3.org/")) {
                semanticClasses.add(ontClass.getURI());
            }
        }
        return semanticClasses;
    }

    public OntModel getOntologyModel() {
        return ontologyModel;
    }

}
