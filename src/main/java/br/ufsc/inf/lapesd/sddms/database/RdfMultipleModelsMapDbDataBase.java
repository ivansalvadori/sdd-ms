package br.ufsc.inf.lapesd.sddms.database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ontology.OntClass;
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
import org.apache.jena.riot.RiotNotFoundException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerCompressionWrapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import br.ufsc.inf.lapesd.csv2rdf.CsvReaderListener;

public class RdfMultipleModelsMapDbDataBase implements DataBase, CsvReaderListener {

    private InfModel currentModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);

    private Set<String> modelIDs = new HashSet<>();

    private String ontologyFile;
    private boolean enableInference;
    private boolean singleRdfOutputFile = true;
    private String resourcePrefix = "";
    private String rdfFolder = "";
    private String ontologyFormat = Lang.N3.getName();
    private String rdfFormat = Lang.NTRIPLES.getName();

    private int writerBatchController = 0;
    protected int resourcesPerFile = 0;
    private String currentFileId = UUID.randomUUID().toString();

    private long totalNumberOfTriples = 0;

    private int mergeModelFactor = 100;

    @PostConstruct
    public void init() {
        readConfigMapping();
        System.out.println("RDF multiple files database with MapDB index");
        indexResources();
    }

    @Override
    public void store(Model model) {
        if (!this.singleRdfOutputFile && this.resourcesPerFile == writerBatchController) {
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
        removeRdfFolderIfExists();
        this.currentModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        this.modelIDs = new HashSet<>();
        this.totalNumberOfTriples = 0;
        this.writerBatchController = 0;
        readConfigMapping();
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
        Set<String> modelIDsThatContainResourceUri = new HashSet<>();
        DB db = DBMaker.fileDB("index.db").fileMmapEnable().readOnly().make();
        Iterable<String> allNames = db.getAllNames();
        for (String mapName : allNames) {
            HTreeMap<String, String> map = db.hashMap(mapName).keySerializer(new SerializerCompressionWrapper(Serializer.STRING)).valueSerializer(new SerializerCompressionWrapper(Serializer.STRING)).open();
            String modelId = map.get(resourceUri);
            if (modelId == null) {
                continue;
            }
            if (modelId.contains("+")) {
                String[] split = StringUtils.split(modelId, "+");
                for (String id : split) {
                    modelIDsThatContainResourceUri.add(id);
                }
            } else {
                modelIDsThatContainResourceUri.add(modelId);
            }
        }

        db.close();

        OntModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        for (String modelId : modelIDsThatContainResourceUri) {
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

        boolean inference = false;

        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF, createOntologyModel());
        OntClass ontClass = model.getOntClass(rdfType);

        ExtendedIterator<OntClass> subClasses = ontClass.listSubClasses();
        while (subClasses.hasNext()) {
            OntClass subClass = subClasses.next();
            if (subClass.isRestriction()) {
                inference = true;
            }
        }

        List<Resource> resources = new ArrayList<>();
        int indexOfRequestedModelId = listModelIds.indexOf(requestedModelId);

        ExtendedIterator<OntClass> eqvClasses = ontClass.listEquivalentClasses();
        while (eqvClasses.hasNext()) {
            OntClass eqvClass = eqvClasses.next();
            if (eqvClass.isRestriction() || eqvClass.isIntersectionClass()) {
                inference = true;
                resources.addAll(this.executeSparql(requestedModelId, rdfType, propertiesAndvalues, inference));
                while (resources.isEmpty() && (indexOfRequestedModelId < listModelIds.size() - 1)) {
                    indexOfRequestedModelId++;
                    String nextModelId = listModelIds.get(indexOfRequestedModelId);
                    resources.addAll(this.executeSparql(nextModelId, rdfType, propertiesAndvalues, inference));
                }
                break;
            } else {
                inference = false;
                resources.addAll(this.executeSparql(requestedModelId, eqvClass.getURI(), propertiesAndvalues, inference));
                while (resources.isEmpty() && (indexOfRequestedModelId < listModelIds.size() - 1)) {
                    indexOfRequestedModelId++;
                    String nextModelId = listModelIds.get(indexOfRequestedModelId);
                    resources.addAll(this.executeSparql(nextModelId, rdfType, propertiesAndvalues, inference));
                }
            }
        }

        for (Resource resource : resources) {
            resourceList.addProperty(ResourceFactory.createProperty("http://sddms.com.br/ontology/" + "items"), resource);
        }

        if (indexOfRequestedModelId < listModelIds.size() - 1) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "next"), "sddms:pageId=" + listModelIds.get(indexOfRequestedModelId + 1));
        }

        if (indexOfRequestedModelId > 0) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "previous"), "sddms:pageId=" + listModelIds.get(indexOfRequestedModelId - 1));
        }

        return resourceModel;
    }

    private List<Resource> executeSparql(String requestedModelId, String rdfType, Map<String, String> propertiesAndvalues, boolean inference) {
        List<Resource> resources = new ArrayList<>();

        InfModel infModel = this.readModelFromFile(requestedModelId);
        if (inference) {
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

        for (String prop : properties) {
            String sparqlFragment = "?resource <%s> \"%s\" .  \n";
            sparqlFragment = String.format(sparqlFragment, prop, propertiesAndvalues.get(prop));
            queryStr.append(sparqlFragment);
        }

        String sparqlFragmentOrderByClause = "?resource <%s> ?orderbyProp .  \n";
        sparqlFragmentOrderByClause = String.format(sparqlFragmentOrderByClause, "http://www.public-security-ontology/dataOcorrencias");

        queryStr.append("} \n");

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, infModel);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            String uri = results.next().getResource("resource").getURI();
            Resource resource = ResourceFactory.createResource(uri);
            resources.add(resource);
        }

        return resources;
    }

    private Model createOntologyModel() {
        String ontologyString = null;
        try {
            ontologyString = new String(Files.readAllBytes(Paths.get(ontologyFile)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        infModel.read(new StringReader(ontologyString), null, this.ontologyFormat);
        return infModel;
    }

    private InfModel readModelFromFile(String modelId) {
        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        try {
            RDFDataMgr.read(infModel, rdfFolder + File.separator + modelId, Lang.NTRIPLES);
        } catch (RiotNotFoundException e) {
            return infModel;
        }
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
        }

        if (new File("index.db").exists()) {
            return;
        }

        int numberOfFiles = files.size();
        int fileCount = 0;
        long totalNumberOfMapEntries = 0;

        DB db = DBMaker.fileDB("index.db").fileMmapEnable().make();
        HTreeMap<String, String> map = db.hashMap("map_" + UUID.randomUUID()).keySerializer(new SerializerCompressionWrapper(Serializer.STRING)).valueSerializer(new SerializerCompressionWrapper(Serializer.STRING)).create();

        for (File file : files) {
            String modelId = file.getName();
            this.modelIDs.add(modelId);

            System.out.println("indexing model " + modelId + " " + (++fileCount) + "/" + numberOfFiles);
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
                modelId = file.getName();
                String existingModelId = map.get(uri);
                if (existingModelId != null) {
                    if (!existingModelId.contains(modelId)) {
                        int modelsAssciactedWithThisUri = StringUtils.split(existingModelId, "+").length;
                        if (modelsAssciactedWithThisUri < mergeModelFactor) {
                            modelId = existingModelId + "+" + modelId;
                        }
                    }
                }

                if (totalNumberOfMapEntries >= 500000) {
                    System.out.println("creating a new index map");
                    map = db.hashMap("map_" + UUID.randomUUID()).keySerializer(new SerializerCompressionWrapper(Serializer.STRING)).valueSerializer(new SerializerCompressionWrapper(Serializer.STRING)).create();
                    totalNumberOfMapEntries = 0;
                }
                map.put(uri, modelId);
                totalNumberOfMapEntries++;

            }

        }
        db.close();
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

    private void readConfigMapping() {
        try (FileInputStream inputStream = FileUtils.openInputStream(new File("mapping.jsonld"))) {
            String mappingContextString = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
            JsonObject mappingJsonObject = new JsonParser().parse(mappingContextString).getAsJsonObject();
            JsonObject mappingConfing = mappingJsonObject.get("@configuration").getAsJsonObject();

            this.ontologyFile = mappingConfing.get("ontologyFile").getAsString();
            this.resourcePrefix = mappingConfing.get("prefix").getAsString();
            this.ontologyFormat = mappingConfing.get("ontologyFormat").getAsString();
            this.enableInference = mappingConfing.get("enableInference").getAsBoolean();
            this.rdfFolder = mappingConfing.get("rdfFolder").getAsString();
            this.resourcesPerFile = mappingConfing.get("resourcesPerFile").getAsInt();
            this.mergeModelFactor = mappingConfing.get("mergeModelFactor").getAsInt();
            this.singleRdfOutputFile = mappingConfing.get("singleRdfOutputFile").getAsBoolean();

        } catch (IOException e) {
            throw new RuntimeException("Mapping file not found");
        }
    }

    @Override
    public void readProcessFinished() {
        this.writeToFile(currentModel, this.currentFileId);
        this.indexResources();
    }

    public void setResourcesPerFile(int resourcesPerFile) {
        this.resourcesPerFile = resourcesPerFile;
    }

    private void removeRdfFolderIfExists() {
        File folder = new File(this.rdfFolder);
        String[] entries = folder.list();

        if (entries == null) {
            return;
        }

        for (String s : entries) {
            File currentFile = new File(folder.getPath(), s);
            currentFile.delete();
        }
        folder.delete();
        File index = new File("index.db");
        index.delete();
    }

}
