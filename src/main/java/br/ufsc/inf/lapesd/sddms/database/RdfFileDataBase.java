package br.ufsc.inf.lapesd.sddms.database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
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
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.beans.factory.annotation.Value;

public class RdfFileDataBase implements DataBase {

    @Value("${config.ontologyFile}")
    private String ontologyFile;

    @Value("${config.enableInference}")
    private boolean enableInference;

    private InfModel currentModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);

    private int insertedStatementsIntoCurrenteModel = 0;
    private Map<String, Set<String>> mapResourcUriModels = new HashMap<>();
    private Set<String> modelIDs = new HashSet<>();

    @PostConstruct
    public void init() {
        System.out.println("RDF files database");
        indexResources();
    }

    public void setOntologyFile(String ontologyFile) {
        this.ontologyFile = ontologyFile;
    }

    @Override
    public void store(Model model) {
        if (insertedStatementsIntoCurrenteModel >= 10000) {
            writeToFile(currentModel);
            currentModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            insertedStatementsIntoCurrenteModel = 0;
        }

        StmtIterator listStatementsmodel = model.listStatements();
        while (listStatementsmodel.hasNext()) {
            currentModel.add(listStatementsmodel.next());
            insertedStatementsIntoCurrenteModel++;
        }
    }

    private void writeToFile(Model model) {
        String modelId = UUID.randomUUID().toString();
        String fileName = "rdf-files/" + modelId;
        try (FileWriter fostream = new FileWriter(fileName, true);) {
            BufferedWriter out = new BufferedWriter(fostream);
            model.write(out, Lang.NTRIPLES.getName());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.modelIDs.add(modelId);
    }

    @Override
    public void resetDataset() {
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
        Set<String> modelIds = this.mapResourcUriModels.get(resourceUri);
        for (String modelId : modelIds) {
            InfModel model = this.readModelFromFile(String.valueOf(modelId));
            Resource resource = model.getResource(resourceUri);
            StmtIterator properties = resource.listProperties();
            while (properties.hasNext()) {
                resourceModel.add(properties.next());
            }
        }
        return resourceModel;
    }

    @Override
    public Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues) {
        InfModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        Resource resourceListType = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "Collection");
        Resource resourceList = resourceModel.createResource("http://sddms.com.br/ontology/" + "ResourceList", resourceListType);

        List<String> listModelIds = new ArrayList<>(this.modelIDs);

        String requestedModelId = listModelIds.get(0);

        if (propertiesAndvalues.get("sddms:pageId") != null) {
            requestedModelId = propertiesAndvalues.get("sddms:pageId");
            propertiesAndvalues.remove("sddms:pageId");
        }

        InfModel infModel = this.readModelFromFile(requestedModelId);

        if (this.enableInference) {
            infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF, this.readModelFromFile(requestedModelId));
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
        int indexOfRequestedModelId = listModelIds.indexOf(requestedModelId);

        if (indexOfRequestedModelId < listModelIds.size() - 1) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "next"), "sddms:pageId=" + listModelIds.get(indexOfRequestedModelId + 1));
        }

        if (indexOfRequestedModelId > 0) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "previous"), "sddms:pageId=" + listModelIds.get(indexOfRequestedModelId - 1));
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

    private InfModel readModelFromFile(String modelId) {
        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        RDFDataMgr.read(infModel, "rdf-files/" + modelId, Lang.NTRIPLES);
        return infModel;
    }

    private void indexResources() {
        Collection<File> files = FileUtils.listFiles(new File("rdf-files/"), null, true);
        for (File file : files) {
            String modelId = file.getName();
            this.modelIDs.add(modelId);
            System.out.println("indexing model " + modelId);
            InfModel model = this.readModelFromFile(modelId);
            ResIterator listSubjects = model.listSubjects();
            while (listSubjects.hasNext()) {
                String uri = listSubjects.next().getURI();
                if (!uri.startsWith("http://sddms-resource/")) {
                    continue;
                }
                Set<String> models = this.mapResourcUriModels.get(uri);
                if (models == null) {
                    models = new HashSet<>();
                }
                models.add(modelId);
                this.mapResourcUriModels.put(uri, models);
            }
        }
        System.out.println("Index created");
    }

    @Override
    public void commit() {
        writeToFile(currentModel);
        indexResources();
    }

}
