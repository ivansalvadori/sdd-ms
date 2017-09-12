package br.ufsc.inf.lapesd.sddms.database;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.springframework.beans.factory.annotation.Value;

public class InMemoryDataBase implements DataBase {

    @Value("${config.ontologyFile}")
    private String ontologyFile;

    @Value("${config.enableInference}")
    private boolean enableInference;

    private final int pageSize = 10;

    private InfModel inmemoryModel = null;

    @PostConstruct
    public void init() {
        inmemoryModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        inmemoryModel.add(createOntologyModel());
        System.out.println("init inmemory");
    }

    public void setOntologyFile(String ontologyFile) {
        this.ontologyFile = ontologyFile;
    }

    @Override
    public void store(Model model) {
        StmtIterator listStatements = model.listStatements();
        while (listStatements.hasNext()) {
            inmemoryModel.add(listStatements.next());
        }
    }

    @Override
    public void resetDataset() {
        inmemoryModel.removeAll();
    }

    @Override
    public List<String> listAllClasses() {
        List<String> rdfTypes = new java.util.ArrayList<>();

        StringBuilder q = new StringBuilder();
        q.append("PREFIX owl: <http://www.w3.org/2002/07/owl#> ");
        q.append("select ?type { ?type a owl:Class }");
        Query query = QueryFactory.create(q.toString());

        QueryExecution qexec = QueryExecutionFactory.create(query, createOntologyModel());
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            QuerySolution next = results.next();
            Resource resource = next.getResource("type");
            // TODO: remove owl concepts
            String uri = resource.getURI();
            if (uri != null && !uri.startsWith("http://www.w3.org/")) {
                rdfTypes.add(uri);
            }
        }

        return rdfTypes;
    }

    @Override
    public Model load(String resourceUri) {
        OntModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, inmemoryModel);
        infModel.add(createOntologyModel());

        Resource resource = infModel.getResource(resourceUri);
        StmtIterator properties = resource.listProperties();
        while (properties.hasNext()) {
            resourceModel.add(properties.next());
        }

        return resourceModel;
    }

    @Override
    public Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues) {
        InfModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        Resource resourceListType = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "Collection");
        Resource resourceList = resourceModel.createResource("http://sddms.com.br/ontology/" + "ResourceList", resourceListType);

        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, this.inmemoryModel);
        if (this.enableInference) {
            infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF, this.inmemoryModel);
        }
        infModel.add(createOntologyModel());

        int requestedOffset = 0;
        if (propertiesAndvalues.get("sddms:pageId") != null) {
            requestedOffset = Integer.parseInt(propertiesAndvalues.get("sddms:pageId"));
            propertiesAndvalues.remove("sddms:pageId");
        }

        StringBuilder queryStr = new StringBuilder();
        queryStr.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n");
        queryStr.append("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n");
        queryStr.append("SELECT ?resource \n");
        queryStr.append("WHERE { \n");
        queryStr.append("?resource a <" + rdfType + "> . \n");

        Set<String> properties = propertiesAndvalues.keySet();
        int indexProperty = 0;

        for (String prop : properties) {
            indexProperty = indexProperty + 1;
            String sparqlFragment = "?resource <%s> ?%s . FILTER( ?%s = \"%s\" ).  \n";
            sparqlFragment = String.format(sparqlFragment, prop, indexProperty, indexProperty, propertiesAndvalues.get(prop));
            queryStr.append(sparqlFragment);
        }

        queryStr.append("}  ");

        if (propertiesAndvalues.get("hydra:next") != null) {
            requestedOffset = Integer.parseInt(propertiesAndvalues.get("hydra:next"));
            propertiesAndvalues.remove("hydra:next");
        }

        String pagination = String.format("limit %s offset %s", this.pageSize, this.pageSize * requestedOffset);
        queryStr.append(pagination);

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, infModel);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            String uri = results.next().getResource("resource").getURI();
            Resource resource = ResourceFactory.createResource(uri);
            resourceList.addProperty(ResourceFactory.createProperty("http://sddms.com.br/ontology/" + "items"), resource);
        }

        resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "next"), "sddms:pageId=" + (requestedOffset + 1));
        if (requestedOffset > 0) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "previous"), "sddms:pageId=" + (requestedOffset - 1));
        }

        qexec.close();
        return resourceModel;
    }

    private Model createOntologyModel() {
        String ontologyString = null;
        try {
            ontologyString = new String(Files.readAllBytes(Paths.get(ontologyFile)));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        infModel.read(new StringReader(ontologyString), null, "N3");
        return infModel;
    }

    @Override
    public void commit() {
        // TODO Auto-generated method stub

    }

}
