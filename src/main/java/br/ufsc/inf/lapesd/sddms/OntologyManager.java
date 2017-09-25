package br.ufsc.inf.lapesd.sddms;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.stereotype.Component;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class OntologyManager {

    public Model loadOntology() {
        JsonObject mappingConfing = createConfigMapping();
        String ontologyFile = mappingConfing.get("ontologyFile").getAsString();
        String ontologyFormat = mappingConfing.get("ontologyFormat").getAsString();

        String ontologyString = null;
        try {
            ontologyString = new String(Files.readAllBytes(Paths.get(ontologyFile)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        InfModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        ontologyModel.read(new StringReader(ontologyString), null, ontologyFormat);
        return ontologyModel;
    }

    public void saveOntology(Model model) {
        JsonObject mappingConfing = createConfigMapping();
        String ontologyFile = mappingConfing.get("ontologyFile").getAsString();
        String ontologyFormat = mappingConfing.get("ontologyFormat").getAsString();

        try (FileWriter fostream = new FileWriter(ontologyFile, false);) {
            BufferedWriter out = new BufferedWriter(fostream);
            model.write(out, ontologyFormat);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private JsonObject createConfigMapping() {
        try (FileInputStream inputStream = FileUtils.openInputStream(new File("mapping.jsonld"))) {
            String mappingContextString = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
            JsonObject mappingJsonObject = new JsonParser().parse(mappingContextString).getAsJsonObject();
            return mappingJsonObject.get("@configuration").getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException("Mapping file not found");
        }
    }

}
