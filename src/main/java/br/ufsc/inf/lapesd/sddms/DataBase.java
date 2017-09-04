package br.ufsc.inf.lapesd.sddms;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.tdb.TDBFactory;
import org.springframework.stereotype.Component;

@Component
public class DataBase {

    private String tdbDirectory = "tdb";

    private String ontologyFile;

    private Dataset dataset;

    public DataBase() {
    }

    public void setOntologyFile(String ontologyFile) {
        this.ontologyFile = ontologyFile;
    }

    public void setDirectory(String directory) {
        this.tdbDirectory = directory;
    }

    public DataBase(String directory) {
        this.tdbDirectory = directory;
        dataset = TDBFactory.createDataset(directory);
    }

    public void store(Model model) {
        dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);
        Model datasetModel = dataset.getDefaultModel();
        datasetModel.add(model);
        dataset.commit();
        dataset.close();
    }

    public void resetDataset() {
        dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);
        dataset.getDefaultModel().removeAll();
        dataset.commit();
        dataset.close();
    }

    public List<String> listAllResources(String rdfType) {
        List<String> resourceUris = new ArrayList<>();
        InfModel infModel = createInfModel();
        StringBuilder q = new StringBuilder();
        q.append("PREFIX owl: <http://www.w3.org/2002/07/owl#> ");
        q.append("SELECT ?x  WHERE { ?x a <%s> }");
        String format = String.format(q.toString(), rdfType);
        Query query = QueryFactory.create(format);
        QueryExecution qexec = QueryExecutionFactory.create(query, infModel);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            QuerySolution next = results.next();
            Resource resource = next.getResource("x");
            resourceUris.add(resource.getURI());
        }

        dataset.close();
        return resourceUris;

    }

    public List<String> listAllClasses() {
        List<String> rdfTypes = new java.util.ArrayList<>();
        InfModel infModel = createInfModel();
        String q = "select distinct ?Concept where {[] a ?Concept}";
        Query query = QueryFactory.create(q);
        QueryExecution qexec = QueryExecutionFactory.create(query, infModel);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            QuerySolution next = results.next();
            Resource resource = next.getResource("Concept");
            // TODO: remove owl concepts
            rdfTypes.add(resource.getURI());
        }

        dataset.close();

        return rdfTypes;

    }

    public Model load(String resourceUri) {
        InfModel infModel = createInfModel();
        OntModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        StmtIterator properties = infModel.getResource(resourceUri).listProperties();
        while (properties.hasNext()) {
            resourceModel.add(properties.next());
        }

        dataset.close();

        return resourceModel;
    }

    private InfModel createInfModel() {
        String ontologyString = null;
        try {
            ontologyString = new String(Files.readAllBytes(Paths.get(ontologyFile)));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);

        Model datasetModel = dataset.getDefaultModel();
        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF, datasetModel);
        infModel.read(new StringReader(ontologyString), null, "N3");
        return infModel;
    }

    public Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues) {
        String ontologyString = null;
        try {
            ontologyString = new String(Files.readAllBytes(Paths.get(ontologyFile)));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);

        Model datasetModel = dataset.getDefaultModel();
        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF, datasetModel);
        infModel.read(new StringReader(ontologyString), null, "N3");

        StringBuilder queryStr = new StringBuilder();
        queryStr.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n");
        queryStr.append("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n");
        queryStr.append("SELECT ?resource \n");
        queryStr.append("WHERE { \n");

        queryStr.append("?resource a <http://www.public-security-ontology/BO> . \n");

        Set<String> properties = propertiesAndvalues.keySet();
        int indexProperty = 0;

        for (String prop : properties) {
            indexProperty = indexProperty + 1;

            String sparqlFragment = "?resource <%s> ?%s . FILTER( ?%s = \"%s\" ) .  \n";

            sparqlFragment = String.format(sparqlFragment, prop, indexProperty, indexProperty, propertiesAndvalues.get(prop));
            queryStr.append(sparqlFragment);
        }

        queryStr.append("} ");

        System.out.println(queryStr);

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, infModel);
        /* Execute the Query */
        ResultSet results = qexec.execSelect();
        // ResultSetFormatter.out(results);
        // ResultSetFormatter.asText(results);

        InfModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);

        while (results.hasNext()) {
            String uri = results.next().getResource("resource").getURI();
            resourceModel.createResource(uri, resourceModel.createResource(rdfType));
        }

        qexec.close();

        resourceModel.write(System.out, "JSON-LD");

        dataset.close();

        return resourceModel;
    }

}
