package br.ufsc.inf.lapesd.sddms;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.springframework.stereotype.Component;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class OntologyManager {

    private OntModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);;

    public Model loadOntology() {
        return createOntologyModel();
    }

    @PostConstruct
    public void init() {
        this.ontologyModel = createOntologyModel();
    }

    public void saveOntology(Model model) {
        JsonObject mappingConfing = readConfigMapping();
        String ontologyFile = mappingConfing.get("ontologyFile").getAsString();
        String ontologyFormat = mappingConfing.get("ontologyFormat").getAsString();

        try (FileWriter fostream = new FileWriter(ontologyFile, false);) {
            model.write(fostream, ontologyFormat);
            this.ontologyModel = createOntologyModel();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected OntModel createOntologyModel() {
        JsonObject mappingConfing = this.readConfigMapping();
        String ontologyFile = mappingConfing.get("ontologyFile").getAsString();
        String ontologyFormat = mappingConfing.get("ontologyFormat").getAsString();

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
            if (!resourceToImport.toString().contains(readConfigMapping().get("prefix").getAsString())) {
                RDFDataMgr.read(model, resourceToImport.toString());
            }
        }

        return model;
    }

    protected JsonObject readConfigMapping() {
        try (FileInputStream inputStream = FileUtils.openInputStream(new File("mapping.jsonld"))) {
            String mappingContextString = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
            JsonObject mappingJsonObject = new JsonParser().parse(mappingContextString).getAsJsonObject();
            JsonObject mappingConfing = mappingJsonObject.get("@configuration").getAsJsonObject();
            return mappingConfing;

        } catch (IOException e) {
            throw new RuntimeException("Mapping file not found");
        }
    }

    public OntModel getOntologyModel() {
        return ontologyModel;
    }

    public void importToOntology(String uri) {
        RDFDataMgr.read(this.ontologyModel, uri);
    }

}
