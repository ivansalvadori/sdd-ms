package br.ufsc.inf.lapesd.sddms.database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

import br.ufsc.inf.lapesd.csv2rdf.CsvReaderListener;

public class RdfMultipleModelsDataBase extends AbstractDataBase implements DataBase, CsvReaderListener {

    private InfModel currentModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);

    private Map<String, Set<String>> mapResourcUriModels = new HashMap<>();
    private Set<String> modelIDs = new HashSet<>();

    @Value("${config.ontologyFile}")
    private String ontologyFile;
    
    
    private boolean enableInference;
    
    @Value("${config.managedUri}")
    private String resourcePrefix = "";
    
    @Value("${config.ontologyFormat}")
    private String ontologyFormat = Lang.N3.getName();
    
    @Value("${config.rdfFormat}")
    private String rdfFormat = Lang.NTRIPLES.getName();

    private int writerBatchController = 0;
    
    @Value("${config.resourcesPerFile}")    
    protected int resourcesPerFile = 0;

    private boolean singleRdfOutputFile = true;
    
    
    private String currentFileId = UUID.randomUUID().toString();

    private long totalNumberOfTriples = 0;
    
    
    @Value("${config.rdfFolder}")
    private String rdfFolder = "tdb";

    @Value("${config.managedUri}")
    private String managedUri = "http://example.com";

    @Value("${config.importExternalWebResources}")
    private boolean importExternalWebResources = false;

    @PostConstruct
    public void init() {
        
        System.out.println("RDF multiple files database");
        indexResources();
    }

    @Override
    public void store(Model model) {
        if (this.singleRdfOutputFile) {
            if (this.writerBatchController == 1000) {
                writeToFile(currentModel, currentFileId);
                this.currentModel.removeAll();
                this.writerBatchController = 0;
            }
        }

        else if (this.resourcesPerFile == writerBatchController) {
            this.currentFileId = UUID.randomUUID().toString();
            writeToFile(this.currentModel, currentFileId);
            this.currentModel.removeAll();
            this.writerBatchController = 0;
        }

        this.currentModel.add(model);
        this.writerBatchController++;
    }

    private void writeToFile(Model model, String fileId) {
        String fileName = this.rdfFolder + "/output_" + fileId + ".ntriples";
        write(model, fileName);
    }

    private void write(Model model, String fileName) {
        File directory = new File(this.rdfFolder);
        if (!directory.exists()) {
            directory.mkdir();
        }

        try (FileWriter fostream = new FileWriter(fileName, true);) {
            BufferedWriter out = new BufferedWriter(fostream);
            model.write(out, this.rdfFormat);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        if (modelIds == null) {
            return resourceModel;
        }
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

        if (listModelIds.isEmpty()) {
            return resourceModel;
        }
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
            String sparqlFragment = "?resource <%s> \"%s\" .  \n";
            sparqlFragment = String.format(sparqlFragment, prop, propertiesAndvalues.get(prop));
            queryStr.append(sparqlFragment);
        }

        String sparqlFragmentOrderByClause = "?resource <%s> ?orderbyProp .  \n";
        sparqlFragmentOrderByClause = String.format(sparqlFragmentOrderByClause, "http://www.public-security-ontology/dataOcorrencias");

        // queryStr.append(sparqlFragmentOrderByClause);

        queryStr.append("} \n");

        // queryStr.append("ORDER BY DESC(?orderbyProp) ");

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

    private InfModel readModelFromFile(String modelId) {
        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        RDFDataMgr.read(infModel, rdfFolder + File.separator + modelId, Lang.NTRIPLES);
        return infModel;
    }

    private void indexResources() {
        File directory = new File(this.rdfFolder);
        if (!directory.exists()) {
            System.out.println("RDF folder not found");
            return;
        }
        Collection<File> files = FileUtils.listFiles(new File(this.rdfFolder), null, true);
        for (File file : files) {
            String modelId = file.getName();
            this.modelIDs.add(modelId);
            System.out.println("indexing model " + modelId);
            InfModel model = this.readModelFromFile(modelId);

            StmtIterator statemants = model.listStatements();
            while (statemants.hasNext()) {
                statemants.next();
                this.totalNumberOfTriples++;
            }

            ResIterator listSubjects = model.listSubjects();
            while (listSubjects.hasNext()) {
                String uri = listSubjects.next().getURI();
                if (!uri.startsWith(resourcePrefix)) {
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
        System.out.println("Tiples: " + this.totalNumberOfTriples);
    }

    @Override
    public void commit() {
    }

    @Override
    public void justRead(Model model) {
        this.store(model);
    }

    public void setResourcePrefix(String resourcePrefix) {
        this.resourcePrefix = resourcePrefix;
    }

    @Override
    public void readProcessFinished() {
        this.writeToFile(currentModel, this.currentFileId);
        this.indexResources();
    }

    public void setResourcesPerFile(int resourcesPerFile) {
        this.resourcesPerFile = resourcesPerFile;
    }

}
