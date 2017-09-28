package br.ufsc.inf.lapesd.sddms.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AbstractDataBase {

    public Model queryTDB(String spqrql) {
        return null;
    }

    public Query createSparql(String rdfType, Map<String, String> propertiesAndvalues, int pageSize, int offset) {
        StringBuilder queryStr = new StringBuilder();
        queryStr.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n");
        queryStr.append("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n");
        queryStr.append("SELECT ?resource \n");
        queryStr.append("WHERE { \n");
        queryStr.append("?resource a <" + rdfType + ">  \n");
        queryStr.append(" . \n");

        Set<String> properties = propertiesAndvalues.keySet();

        for (String prop : properties) {
            Set<String> eqvProperties = this.getEqvProperty(prop);
            if (eqvProperties == null) {
                String sparqlFragment = "?resource <%s> \"%s\"  . \n";
                sparqlFragment = String.format(sparqlFragment, prop, propertiesAndvalues.get(prop));
                queryStr.append(sparqlFragment);
            } else {
                String sparqlFragment = "?resource <%s>";
                sparqlFragment = String.format(sparqlFragment, prop);
                queryStr.append(sparqlFragment);

                for (String eqvPro : eqvProperties) {
                    sparqlFragment = " | <%s> ";
                    sparqlFragment = String.format(sparqlFragment, eqvPro);
                    queryStr.append(sparqlFragment);
                }
                sparqlFragment = "\"%s\"  . \n";
                sparqlFragment = String.format(sparqlFragment, propertiesAndvalues.get(prop));
                queryStr.append(sparqlFragment);
            }
        }

        String sparqlFragmentOrderByClause = "ORDER BY DESC(?resource) .  \n";
        sparqlFragmentOrderByClause = String.format(sparqlFragmentOrderByClause, "http://www.public-security-ontology/dataOcorrencias");

        queryStr.append("} \n");
        String pagination = String.format("limit %s offset %s", pageSize, pageSize * offset);
        queryStr.append(pagination);
        Query query = QueryFactory.create(queryStr.toString());
        return query;
    }

    protected Set<String> getEqvProperty(String propUri) {
        Resource property = this.createOntologyModel().getResource(propUri);
        if (property != null) {
            NodeIterator eqvList = this.createOntologyModel().listObjectsOfProperty(property, OWL.equivalentProperty);
            Set<String> eqvProperties = new TreeSet<>();
            while (eqvList.hasNext()) {
                eqvProperties.add(eqvList.next().toString());
            }
            return eqvProperties;
        }
        return null;
    }

    protected Set<String> getEqvClasses(String classUri) {
        Resource property = this.createOntologyModel().getResource(classUri);
        if (property != null) {
            NodeIterator eqvList = this.createOntologyModel().listObjectsOfProperty(property, OWL.equivalentClass);
            Set<String> eqvClasses = new TreeSet<>();
            while (eqvList.hasNext()) {
                eqvClasses.add(eqvList.next().toString());
            }
            return eqvClasses;
        }
        return null;
    }

    protected OntModel createOntologyModel() {
        JsonObject mappingConfing = this.readConfigMapping();
        String ontologyFile = mappingConfing.get("ontologyFile").getAsString();
        String ontologyFormat = mappingConfing.get("ontologyFormat").getAsString();

        String ontologyString = null;
        try {
            ontologyString = new String(Files.readAllBytes(Paths.get(ontologyFile)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        model.read(new StringReader(ontologyString), null, ontologyFormat);
        return model;
    }

    protected JsonObject readConfigMapping() {
        try (FileInputStream inputStream = FileUtils.openInputStream(new File("mapping.jsonld"))) {
            String mappingContextString = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
            JsonObject mappingJsonObject = new JsonParser().parse(mappingContextString).getAsJsonObject();
            JsonObject mappingConfing = mappingJsonObject.get("@configuration").getAsJsonObject();
            return mappingConfing;

        } catch (IOException e) {
            throw new RuntimeException("Mapping file not found");
        }
    }

    protected boolean hasSameAs(String uri) {
        Resource resource = this.createOntologyModel().getResource(uri);
        if (resource != null && resource.hasProperty(OWL.sameAs)) {
            return true;
        }
        return false;
    }

}
