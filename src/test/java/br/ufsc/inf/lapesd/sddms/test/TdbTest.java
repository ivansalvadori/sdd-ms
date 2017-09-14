package br.ufsc.inf.lapesd.sddms.test;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.tdb.TDBFactory;
import org.junit.Test;

import br.ufsc.inf.lapesd.sddms.OwlRestrictionReasoner;
import br.ufsc.inf.lapesd.sddms.database.TDBMultipleModelsDataBase;

public class TdbTest {

    @Test
    public void mustLoadModel() throws IOException {

        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb";
        Dataset dataset = TDBFactory.createDataset(directory);

        dataset.begin(ReadWrite.WRITE);
        // Get model inside the transaction
        Model model = dataset.getDefaultModel();

        String ontology = IOUtils.toString(this.getClass().getResourceAsStream("/ontology.owl"), "UTF-8");
        model.read(new StringReader(ontology), null, "N3");

        String data = IOUtils.toString(this.getClass().getResourceAsStream("/data.rdf"), "UTF-8");
        model.read(new StringReader(data), null, "N3");
        dataset.commit();
        dataset.close();
    }

    @Test
    public void mustLoadClasses() throws IOException {

        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb";

        String ontologyModel = IOUtils.toString(this.getClass().getResourceAsStream("/ontology.owl"), "UTF-8");
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        ontModel.read(new StringReader(ontologyModel), null, "N3");

        OwlRestrictionReasoner filter = new OwlRestrictionReasoner();
        filter.init(directory, ontModel);

        List<String> listSemanticClasses = filter.listSemanticClasses();
        for (String string : listSemanticClasses) {
            System.out.println(string);
        }

        List<Resource> person = filter.listAllIndividuals("http://api.com#Person");
        for (Resource resource : person) {
            System.out.println(resource);
        }

    }

    @Test
    public void mustLoadBrasileirosAssasinados() throws IOException {

        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb";

        String ontologyModel = IOUtils.toString(this.getClass().getResourceAsStream("/ontology.owl"), "UTF-8");
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        ontModel.read(new StringReader(ontologyModel), null, "N3");

        OwlRestrictionReasoner filter = new OwlRestrictionReasoner();
        filter.init(directory, ontModel);

        List<Resource> person = filter.listAllIndividuals("http://api.com#BrasileiroAssassinado");
        for (Resource resource : person) {
            System.out.println(resource);
        }
    }

    @Test
    public void mustLoadBrasileirosAssasinadosEquivalentClass() throws IOException {
        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb";

        String ontologyModel = IOUtils.toString(this.getClass().getResourceAsStream("/ontology.owl"), "UTF-8");
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        ontModel.read(new StringReader(ontologyModel), null, "N3");

        OwlRestrictionReasoner filter = new OwlRestrictionReasoner();
        filter.init(directory, ontModel);

        List<Resource> person = filter.listAllIndividuals("http://api.com#BrazilianMurdered");
        for (Resource resource : person) {
            System.out.println(resource);
        }
    }

    @Test
    public void mustxx() throws IOException {
        // Make a TDB-backed dataset
        String directory = "C:\\Development\\eclipse-workspace\\sdd-ms\\tdb";
        String ontologyFile = "C:\\Development\\eclipse-workspace\\sdd-ms\\ontology.owl";

        TDBMultipleModelsDataBase dataBase = new TDBMultipleModelsDataBase();
        dataBase.setDirectory(directory);
        dataBase.setOntologyFile(ontologyFile);

        Map<String, String> propValues = new HashMap<>();
        propValues.put("http://www.public-security-ontology/numeroOcorrencia", "888251/2017");
        propValues.put("http://www.public-security-ontology/dataOcorrencia", "30/06/2017");

        dataBase.queryTDB("http://www.public-security-ontology/BoletimOcorrencia", propValues);
    }

}
