package br.ufsc.inf.lapesd.sddms.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
import org.apache.jena.riot.Lang;
import org.apache.jena.tdb.TDBFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import br.ufsc.inf.lapesd.csv2rdf.CsvReaderListener;

public class TDBMultipleModelsDataBase implements DataBase, CsvReaderListener {

    private String tdbDirectory = "tdb";

    private int modelIndex = 0;

    private InfModel inmemoryTempModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);

    private String ontologyFile;
    private boolean enableInference;
    private String ontologyFormat = Lang.N3.getName();

    private int writerBatchController = 0;
    protected int resourcesPerFile = 0;

    @PostConstruct
    public void init() {
        System.out.println("TDB multiple models databse");

        JsonObject mappingConfing = createConfigMapping();
        this.ontologyFile = mappingConfing.get("ontologyFile").getAsString();
        this.ontologyFormat = mappingConfing.get("ontologyFormat").getAsString();
        this.enableInference = mappingConfing.get("enableInference").getAsBoolean();
        this.resourcesPerFile = mappingConfing.get("resourcesPerFile").getAsInt();
    }

    @Override
    public void store(Model model) {
        if (writerBatchController == resourcesPerFile) {
            System.out.println("Created model " + modelIndex);
            this.persist(this.inmemoryTempModel);
            this.inmemoryTempModel.removeAll();
            writerBatchController = 0;
            this.modelIndex++;
        }

        this.inmemoryTempModel.add(model);
        writerBatchController++;
    }

    public void persist(Model model) {
        Dataset dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);
        Model namedModel = dataset.getNamedModel(String.valueOf(this.modelIndex));
        namedModel.add(model);
        dataset.commit();
        dataset.close();
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

        Dataset dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.READ);

        List<Model> models = gettAllModels(dataset);
        for (Model model : models) {
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
        }
        infModel.add(namedModel);
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

        queryStr.append("} ");

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, infModel);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            String uri = results.next().getResource("resource").getURI();
            Resource resource = ResourceFactory.createResource(uri);
            resourceList.addProperty(ResourceFactory.createProperty("http://sddms.com.br/ontology/" + "items"), resource);

        }

        if (requestedModel < this.gettAllModels(dataset).size() - 1) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "next"), "sddms:pageId=" + (requestedModel + 1));
        }

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
        infModel.read(new StringReader(ontologyString), null, this.ontologyFormat);
        return infModel;
    }

    @Override
    public void commit() {
        this.persist(inmemoryTempModel);
    }

    @Override
    public void justRead(Model model) {
        this.store(model);
    }

    @Override
    public void readProcessFinished() {
        this.persist(inmemoryTempModel);
    }

    public void setOntologyFile(String ontologyFile) {
        this.ontologyFile = ontologyFile;
    }

    public void setDirectory(String directory) {
        this.tdbDirectory = directory;
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
