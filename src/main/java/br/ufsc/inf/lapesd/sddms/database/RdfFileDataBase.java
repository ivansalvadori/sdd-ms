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
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private int modelIndex = 0;
    private int insertedStatementsIntoCurrenteModel = 0;
    private Map<String, List<Integer>> mapResourcUriModels = new HashMap<>();

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
            this.modelIndex++;
            currentModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            insertedStatementsIntoCurrenteModel = 0;
        }

        StmtIterator listStatementsmodel = model.listStatements();
        while (listStatementsmodel.hasNext()) {
            currentModel.add(listStatementsmodel.next());
            insertedStatementsIntoCurrenteModel++;
        }

        ResIterator listSubjects = model.listSubjects();
        while (listSubjects.hasNext()) {
            String uri = listSubjects.next().getURI();
            if (!uri.startsWith("http://sddms-resource/")) {
                continue;
            }
            List<Integer> models = this.mapResourcUriModels.get(uri);
            if (models == null) {
                models = new ArrayList<>();
            }
            models.add(this.modelIndex);
            this.mapResourcUriModels.put(uri, models);
        }
    }

    private void writeToFile(Model model) {
        String fileName = "rdf-files/" + this.modelIndex;
        try (FileWriter fostream = new FileWriter(fileName, false);) {
            BufferedWriter out = new BufferedWriter(fostream);
            model.write(out, Lang.RDFXML.getName());
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

        List<Integer> modelIndexes = this.mapResourcUriModels.get(resourceUri);
        for (Integer modelIndex : modelIndexes) {
            InfModel model = this.readModelFromFile(String.valueOf(modelIndex));
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

        String requestedModel = "0";

        if (propertiesAndvalues.get("sddms:pageId") != null) {
            requestedModel = propertiesAndvalues.get("sddms:pageId");
            propertiesAndvalues.remove("sddms:pageId");
        }

        InfModel infModel = this.readModelFromFile(requestedModel);

        if (this.enableInference) {
            infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF, this.readModelFromFile(requestedModel));
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
        int requestedModelInt = Integer.parseInt(requestedModel);
        resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "next"), "sddms:pageId=" + (requestedModelInt + 1));
        if (requestedModelInt > 0) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "previous"), "sddms:pageId=" + (requestedModelInt - 1));
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

    private InfModel readModelFromFile(String modelIndex) {
        InfModel infModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        RDFDataMgr.read(infModel, "rdf-files/" + modelIndex, Lang.RDFXML);
        return infModel;
    }

    private void indexResources() {
        Collection<File> files = FileUtils.listFiles(new File("rdf-files/"), null, true);
        for (File file : files) {
            String modelIndex = file.getName();
            System.out.println("reading model " + modelIndex);
            InfModel model = this.readModelFromFile(modelIndex);
            ResIterator listSubjects = model.listSubjects();
            while (listSubjects.hasNext()) {
                String uri = listSubjects.next().getURI();
                if (!uri.startsWith("http://sddms-resource/")) {
                    continue;
                }
                List<Integer> models = this.mapResourcUriModels.get(uri);
                if (models == null) {
                    models = new ArrayList<>();
                }
                models.add(Integer.parseInt(modelIndex));
                this.mapResourcUriModels.put(uri, models);
            }
        }
        System.out.println("Index created");
    }

    @Override
    public void commit() {
        writeToFile(currentModel);
    }

}
