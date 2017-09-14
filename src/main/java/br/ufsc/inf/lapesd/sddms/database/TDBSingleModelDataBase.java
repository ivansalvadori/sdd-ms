package br.ufsc.inf.lapesd.sddms.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.riot.Lang;
import org.apache.jena.tdb.TDBFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import br.ufsc.inf.lapesd.csv2rdf.CsvReaderListener;

public class TDBSingleModelDataBase implements DataBase, CsvReaderListener {

    private String tdbDirectory = "tdb";
    private InfModel currentModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
    private final int pageSize = 10;

    private Reasoner dataReasoner;
    private final String owlrules = "owl-fb.rules";

    private String ontologyFile;
    private boolean enableInference;
    private String ontologyFormat = Lang.N3.getName();

    private int writerBatchController = 0;
    protected int resourcesPerFile = 0;

    @PostConstruct
    public void init() {
        System.out.println("TDB single model database");
        dataReasoner = createReasoner(owlrules);

        JsonObject mappingConfing = createConfigMapping();
        this.ontologyFile = mappingConfing.get("ontologyFile").getAsString();
        this.ontologyFormat = mappingConfing.get("ontologyFormat").getAsString();
        this.enableInference = mappingConfing.get("enableInference").getAsBoolean();
        this.resourcesPerFile = mappingConfing.get("resourcesPerFile").getAsInt();
    }

    private GenericRuleReasoner createReasoner(String... rulesFiles) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<Rule> rules = new ArrayList<>();
        for (String rulesFile : rulesFiles) {
            try (InputStream in = cl.getResourceAsStream(rulesFile); BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                rules.addAll(Rule.parseRules(Rule.rulesParserFromReader(reader)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        GenericRuleReasoner gReasoner = new GenericRuleReasoner(rules);
        gReasoner.setMode(GenericRuleReasoner.BACKWARD);
        return gReasoner;
    }

    @Override
    public void store(Model model) {
        if (writerBatchController == 1000) {
            persit(currentModel);
            currentModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            writerBatchController = 0;
        }
        currentModel.add(model);
        writerBatchController++;
    }

    @Override
    public void resetDataset() {
    }

    private void persit(Model model) {
        Dataset dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);
        Model tdbDefaultModel = dataset.getDefaultModel();

        StmtIterator listStatements = model.listStatements();
        while (listStatements.hasNext()) {
            tdbDefaultModel.add(listStatements.next());
        }

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

        Resource createResource = resourceModel.createResource(resourceUri);

        Dataset dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.READ);

        StringBuilder queryStr = new StringBuilder();
        String sparqlFragment = String.format("SELECT ?p ?o { <%s> ?p ?o} ", resourceUri);
        queryStr.append(sparqlFragment);

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, dataset.getDefaultModel());
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            QuerySolution next = results.next();
            Resource predicate = next.getResource("p");
            RDFNode rdfNode = next.get("o");
            createResource.addProperty(resourceModel.createProperty(predicate.getURI()), rdfNode);
        }

        dataset.close();
        return resourceModel;
    }

    @Override
    public Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues) {
        Dataset dataset = TDBFactory.createDataset(tdbDirectory);
        dataset.begin(ReadWrite.WRITE);

        InfModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        Resource resourceListType = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "Collection");
        Resource resourceList = resourceModel.createResource("http://sddms.com.br/ontology/" + "ResourceList", resourceListType);

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
            String sparqlFragment = "?resource <%s> \"%s\" .  \n";
            sparqlFragment = String.format(sparqlFragment, prop, propertiesAndvalues.get(prop));
            queryStr.append(sparqlFragment);
        }

        queryStr.append("}   ");

        String pagination = String.format("limit %s offset %s", this.pageSize, this.pageSize * requestedOffset);
        queryStr.append(pagination);

        QueryExecution qexec = QueryExecutionFactory.create(queryStr.toString(), dataset);
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
        dataset.close();
        return resourceModel;
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

    @Override
    public void commit() {
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

    @Override
    public void readProcessFinished() {
        this.persit(this.currentModel);
    }

    @Override
    public void justRead(Model model) {
        this.store(model);
    }

    public void setOntologyFile(String ontologyFile) {
        this.ontologyFile = ontologyFile;
    }

    public void setDirectory(String directory) {
        this.tdbDirectory = directory;
    }

}