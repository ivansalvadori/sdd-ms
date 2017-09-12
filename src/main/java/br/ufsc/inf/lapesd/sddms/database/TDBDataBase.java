package br.ufsc.inf.lapesd.sddms.database;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

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
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.tdb.TDBFactory;
import org.springframework.beans.factory.annotation.Value;

public class TDBDataBase implements DataBase {

    private String tdbDirectory = "tdb";

    private int modelIndex = 0;

    private InfModel inmemoryTempModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);

    @Value("${config.ontologyFile}")
    private String ontologyFile;

    @Value("${config.enableInference}")
    private boolean enableInference;

    @PostConstruct
    public void init() {
        System.out.println("init");
    }

    public TDBDataBase() {
    }

    public void setOntologyFile(String ontologyFile) {
        this.ontologyFile = ontologyFile;
    }

    public void setDirectory(String directory) {
        this.tdbDirectory = directory;
    }

    public TDBDataBase(String directory) {
        this.tdbDirectory = directory;
    }

    int insertedStatementsIntoCurrenteModel = 0;

    @Override
    public void store(Model model) {
        if (insertedStatementsIntoCurrenteModel >= 10000) {
            InfModel inmemoryModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            System.out.println("Created model " + modelIndex);
            insertedStatementsIntoCurrenteModel = 0;
            this.persist(inmemoryModel);
            this.inmemoryTempModel.removeAll();
        }

        StmtIterator listStatementsmodel = model.listStatements();
        while (listStatementsmodel.hasNext()) {
            this.inmemoryTempModel.add(listStatementsmodel.next());
            insertedStatementsIntoCurrenteModel++;
        }
    }

    public void persist(Model model) {
        Dataset dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);
        Model namedModel = dataset.getNamedModel(String.valueOf(this.modelIndex));

        StmtIterator listStatements = inmemoryTempModel.listStatements();
        while (listStatements.hasNext()) {
            namedModel.add(listStatements.next());
        }

        dataset.commit();
        dataset.close();
        System.out.println("Persited");

        this.inmemoryTempModel.removeAll();
        this.modelIndex++;
    }

    @Override
    public void resetDataset() {
        Dataset dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);
        dataset.getDefaultModel().removeAll();
        dataset.commit();
        dataset.close();
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
        int x = 0;

        Dataset dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.READ);

        List<Model> models = gettAllModels(dataset);
        for (Model model : models) {
            System.out.println("model " + x++);

            Resource resource = model.getResource(resourceUri);
            StmtIterator properties = resource.listProperties();
            while (properties.hasNext()) {
                resourceModel.add(properties.next());
            }
        }

        dataset.end();
        return resourceModel;
    }

    private List<Model> gettAllModels(Dataset dataset) {
        List<Model> models = new ArrayList<>();
        dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.READ);
        Iterator<String> listNames = dataset.listNames();
        while (listNames.hasNext()) {
            String modelName = listNames.next();
            Model namedModel = dataset.getNamedModel(modelName);
            models.add(namedModel);
        }

        return models;
    }

    @Override
    public Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues) {
        Dataset dataset = TDBFactory.createDataset(tdbDirectory);

        InfModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        Resource resourceListType = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "Collection");
        Resource resourceList = resourceModel.createResource("http://sddms.com.br/ontology/" + "ResourceList", resourceListType);

        int requestedModel = 0;

        if (propertiesAndvalues.get("sddms:pageId") != null) {
            requestedModel = Integer.parseInt(propertiesAndvalues.get("sddms:pageId"));
            propertiesAndvalues.remove("sddms:pageId");
        }

        dataset.begin(ReadWrite.READ);
        Model namedModel = dataset.getNamedModel(String.valueOf(requestedModel));
        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        if (this.enableInference) {
            infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
            infModel.add(namedModel);
        }
        infModel.add(createOntologyModel());

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

        queryStr.append("} ");

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, infModel);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            String uri = results.next().getResource("resource").getURI();
            Resource resource = ResourceFactory.createResource(uri);
            resourceList.addProperty(ResourceFactory.createProperty("http://sddms.com.br/ontology/" + "items"), resource);

        }
        resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "next"), "sddms:pageId=" + (requestedModel + 1));
        if (requestedModel > 0) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "previous"), "sddms:pageId=" + (requestedModel - 1));
        }

        qexec.close();
        dataset.end();
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
        this.persist(inmemoryTempModel);

    }
}
