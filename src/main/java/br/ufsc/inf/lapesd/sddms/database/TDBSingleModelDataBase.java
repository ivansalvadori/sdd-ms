package br.ufsc.inf.lapesd.sddms.database;

import java.util.HashSet;
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
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.vocabulary.OWL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class TDBSingleModelDataBase extends AbstractDataBase implements DataBase {

    private static final Logger logger = LoggerFactory.getLogger(TDBSingleModelDataBase.class);

    @Value("${config.tdbPath}")
    private String tdbPath = "tdb";

    @Value("${config.managedUri}")
    private String managedUri = "http://example.com";

    @Value("${config.importExternalWebResources}")
    private boolean importExternalWebResources = false;

    private int pageSize = 50;

    @PostConstruct
    public void init() {
        logger.info("Initializing TDB single graph database");
    }

    @Override
    public void store(Model model) {
        // todo
    }

    @Override
    public void resetDataset() {
        // todo
    }

    @Override
    public List<String> listAllClasses() {
        List<String> rdfTypes = new java.util.ArrayList<>();

        StringBuilder q = new StringBuilder();
        q.append("PREFIX owl: <http://www.w3.org/2002/07/owl#> ");
        q.append("select ?type { ?type a owl:Class }");
        Query query = QueryFactory.create(q.toString());

        QueryExecution qexec = QueryExecutionFactory.create(query, this.ontologyManager.getOntologyModel());
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
        Dataset dataset = TDBFactory.createDataset(tdbPath);
        dataset.begin(ReadWrite.READ);
        Model ontologyModel = super.ontologyManager.getOntologyModel();

        OntModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);

        if (this.hasSameAs(resourceUri)) {
            NodeIterator sameAsList = ontologyModel.listObjectsOfProperty(ontologyModel.getResource(resourceUri), OWL.sameAs);
            while (sameAsList.hasNext()) {
                RDFNode next = sameAsList.next();
                if (!next.toString().contains(this.managedUri) && importExternalWebResources) {
                    try {
                        RDFDataMgr.read(resourceModel, next.toString(), Lang.RDFXML);
                    } catch (Exception e) {
                        resourceModel.add(findAndPopulate(next.toString(), dataset.getDefaultModel()));
                    }
                } else {
                    resourceModel.add(findAndPopulate(next.toString(), dataset.getDefaultModel()));
                }
            }
        } else {
            resourceModel.add(findAndPopulate(resourceUri, dataset.getDefaultModel()));

        }

        OntModel finalResource = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        StmtIterator properties = resourceModel.getResource(resourceUri).listProperties();
        while (properties.hasNext()) {
            Statement next = properties.next();
            finalResource.add(next);
        }

        dataset.close();
        return finalResource;
    }

    private OntModel findAndPopulate(String resourceUri, Model model) {
        OntModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        Model ontologyModel = super.ontologyManager.getOntologyModel();
        resourceModel.add(ontologyModel);

        Resource createResource = resourceModel.createResource(resourceUri);
        StringBuilder queryStr = new StringBuilder();
        String sparqlFragment = String.format("SELECT ?p ?o { <%s> ?p ?o} ", resourceUri);
        queryStr.append(sparqlFragment);

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, model);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            QuerySolution next = results.next();
            Resource predicate = next.getResource("p");
            RDFNode rdfNode = next.get("o");
            createResource.addProperty(resourceModel.createProperty(predicate.getURI()), rdfNode);
        }

        return resourceModel;
    }

    @Override
    public Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues) {
        Dataset dataset = TDBFactory.createDataset(tdbPath);
        dataset.begin(ReadWrite.READ);

        OntModel ontologyModel = super.ontologyManager.getOntologyModel();
        ontologyModel.setStrictMode(false);

        int requestedOffset = 0;
        if (propertiesAndvalues.get("sddms:pageId") != null) {
            requestedOffset = Integer.parseInt(propertiesAndvalues.get("sddms:pageId"));
            propertiesAndvalues.remove("sddms:pageId");
        }

        InfModel resourceModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        Resource resourceListType = resourceModel.createResource("https://www.w3.org/ns/hydra/core#" + "Collection");
        Resource resourceList = resourceModel.createResource("http://sddms.com.br/ontology/" + "ResourceList", resourceListType);

        Set<String> eqvAndSubClasses = new HashSet<>();
        eqvAndSubClasses.addAll(super.getEqvClasses(rdfType));
        eqvAndSubClasses.addAll(super.getSubClasses(rdfType));

        int fetchedResources = 0;
        if (!eqvAndSubClasses.isEmpty()) {
            for (String eqvClass : eqvAndSubClasses) {
                fetchedResources = fetchedResources + loadResourcesFromDataModel(eqvClass, propertiesAndvalues, dataset, pageSize, requestedOffset, resourceList);
                fetchedResources = fetchedResources + loadResourcesFromDataModel(eqvClass, propertiesAndvalues, ontologyModel, pageSize, requestedOffset, resourceList);
            }
        } else {
            fetchedResources = loadResourcesFromDataModel(rdfType, propertiesAndvalues, dataset, pageSize, requestedOffset, resourceList);
            fetchedResources = loadResourcesFromDataModel(rdfType, propertiesAndvalues, ontologyModel, pageSize, requestedOffset, resourceList);
        }

        fetchedResources = processRestrictions(rdfType, propertiesAndvalues);

        if (fetchedResources > 0) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "next"), "sddms:pageId=" + (requestedOffset + 1));
        }
        if (requestedOffset > 0) {
            resourceList.addProperty(ResourceFactory.createProperty("https://www.w3.org/ns/hydra/core#" + "previous"), "sddms:pageId=" + (requestedOffset - 1));
        }

        dataset.close();

        return resourceModel;
    }

    private int processRestrictions(String rdfType, Map<String, String> propertiesAndvalues) {
        super.getRestrictions(rdfType);

        return 0;
    }

    private int loadResourcesFromDataModel(String rdfType, Map<String, String> propertiesAndvalues, Model model, int pageSize, int requestedOffset, Resource resourceList) {
        int resourcesFetched = 0;
        Query query;
        query = createSparql(rdfType, propertiesAndvalues, pageSize, requestedOffset);
        QueryExecution qexec = QueryExecutionFactory.create(query, model);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            resourcesFetched++;
            String uri = results.next().getResource("resource").getURI();
            Resource resource = ResourceFactory.createResource(uri);
            if (this.hasSameAs(uri)) {
                OntModel ontologyModel = super.ontologyManager.getOntologyModel();
                NodeIterator sameAsList = ontologyModel.listObjectsOfProperty(ontologyModel.getResource(uri), OWL.sameAs);
                while (sameAsList.hasNext()) {
                    Resource sameAsResource = ontologyModel.createResource(sameAsList.next().toString());
                    resourceList.addProperty(ResourceFactory.createProperty("http://sddms.com.br/ontology/" + "items"), sameAsResource);
                }
            }
            resourceList.addProperty(ResourceFactory.createProperty("http://sddms.com.br/ontology/" + "items"), resource);
        }
        qexec.close();
        return resourcesFetched;
    }

    private int loadResourcesFromDataModel(String rdfType, Map<String, String> propertiesAndvalues, Dataset dataset, int pageSize, int requestedOffset, Resource resourceList) {
        int resourcesFetched = 0;
        Query query;
        query = createSparql(rdfType, propertiesAndvalues, pageSize, requestedOffset);
        QueryExecution qexec = QueryExecutionFactory.create(query, dataset);
        ResultSet results = qexec.execSelect();

        while (results.hasNext()) {
            resourcesFetched++;
            String uri = results.next().getResource("resource").getURI();
            Resource resource = ResourceFactory.createResource(uri);
            if (this.hasSameAs(uri)) {
                OntModel ontologyModel = super.ontologyManager.getOntologyModel();
                NodeIterator sameAsList = ontologyModel.listObjectsOfProperty(ontologyModel.getResource(uri), OWL.sameAs);
                while (sameAsList.hasNext()) {
                    Resource sameAsResource = ontologyModel.createResource(sameAsList.next().toString());
                    resourceList.addProperty(ResourceFactory.createProperty("http://sddms.com.br/ontology/" + "items"), sameAsResource);
                }
            }
            resourceList.addProperty(ResourceFactory.createProperty("http://sddms.com.br/ontology/" + "items"), resource);
        }
        qexec.close();
        return resourcesFetched;
    }

    @Override
    public void commit() {
        // TODO Auto-generated method stub

    }

}
