package br.ufsc.inf.lapesd.sddms.database;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.Restriction;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.JsonObject;

import br.ufsc.inf.lapesd.sddms.OntologyManager;

public class AbstractDataBase {

    @Autowired
    protected OntologyManager ontologyManager;

    public Model queryTDB(String spqrql) {
        return null;
    }

    public Query createSparql(String rdfType, Map<String, String> propertiesAndvalues, int pageSize, int offset) {
        List<String> objectPropertiesList = new ArrayList<>();
        OntModel ontologyModel = this.ontologyManager.getOntologyModel();
        ExtendedIterator<ObjectProperty> objectProperties = ontologyModel.listObjectProperties();
        while (objectProperties.hasNext()) {
            ObjectProperty next = objectProperties.next();
            objectPropertiesList.add(next.getURI());
        }

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
            String propertyValue = propertiesAndvalues.get(prop);
            if (eqvProperties == null || eqvProperties.isEmpty()) {
                String sparqlFragment = "?resource <%s> \"%s\"  . \n";
                if (objectPropertiesList.contains(prop)) {
                    sparqlFragment = "{ ?resource <%s> <%s> } ";
                    sparqlFragment = String.format(sparqlFragment, prop, propertyValue);
                    queryStr.append(sparqlFragment);

                    if (this.hasSameAs(propertyValue)) {
                        NodeIterator sameAsList = ontologyModel.listObjectsOfProperty(ontologyModel.getResource(propertyValue), OWL.sameAs);
                        while (sameAsList.hasNext()) {
                            sparqlFragment = " UNION { ?resource <%s> <%s> } ";
                            sparqlFragment = String.format(sparqlFragment, prop, sameAsList.next());
                            queryStr.append(sparqlFragment);
                        }
                        sparqlFragment = " . \n";
                        queryStr.append(sparqlFragment);
                    }
                } else {
                    sparqlFragment = "{ ?resource <%s> \"%s\" } ";
                    sparqlFragment = String.format(sparqlFragment, prop, propertyValue);
                    queryStr.append(sparqlFragment);

                }
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
                if (objectPropertiesList.contains(prop)) {
                    sparqlFragment = " <%s>  . \n";
                    if (this.hasSameAs(propertyValue)) {
                        // TODO: not implemented yet
                    }
                }
                sparqlFragment = String.format(sparqlFragment, propertyValue);
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
        Resource property = this.ontologyManager.getOntologyModel().getResource(propUri);

        if (property != null) {
            NodeIterator eqvList = this.ontologyManager.getOntologyModel().listObjectsOfProperty(property, OWL.equivalentProperty);
            Set<String> eqvProperties = new TreeSet<>();
            while (eqvList.hasNext()) {
                eqvProperties.add(eqvList.next().toString());
            }
            return eqvProperties;
        }
        return null;
    }

    protected Set<String> getEqvClasses(String classUri) {
        Resource property = this.ontologyManager.getOntologyModel().getResource(classUri);
        if (property != null) {
            NodeIterator eqvList = this.ontologyManager.getOntologyModel().listObjectsOfProperty(property, OWL.equivalentClass);
            Set<String> eqvClasses = new TreeSet<>();
            while (eqvList.hasNext()) {
                eqvClasses.add(eqvList.next().toString());
            }
            return eqvClasses;
        }
        return null;
    }

    protected Set<String> getSubClasses(String classUri) {
        Set<String> subClasses = new HashSet<>();
        OntClass ontClass = this.ontologyManager.getOntologyModel().getOntClass(classUri);
        boolean hasSubClass = ontClass.hasSubClass();
        if (ontClass != null && hasSubClass) {
            List<OntClass> subOntClasses = ontClass.listSubClasses().toList();
            for (OntClass classe : subOntClasses) {
                subClasses.add(classe.getURI());
            }
            return subClasses;
        }
        return null;
    }

    protected Set<String> getRestrictions(String classUri) {
        ExtendedIterator<Restriction> listRestrictions = this.ontologyManager.getOntologyModel().listRestrictions();
        while (listRestrictions.hasNext()) {
            Restriction restriction = listRestrictions.next();
            OntProperty onProperty = restriction.getOnProperty();
            RDFNode propertyValue = restriction.getPropertyValue(OWL2.hasValue);
            // System.out.println(onProperty);
            // System.out.println(propertyValue);

        }

        OntClass ontoClass = this.ontologyManager.getOntologyModel().getOntClass(classUri);

        ExtendedIterator<OntClass> listEquivalentClasses = ontoClass.listEquivalentClasses();
        while (listEquivalentClasses.hasNext()) {
            OntClass next = listEquivalentClasses.next();
            if (next != null) {
                boolean intersectionClass = next.isIntersectionClass();
                if (intersectionClass) {
                    OntModel restrictionClassModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
                    StmtIterator listProperties = next.listProperties();
                    while (listProperties.hasNext()) {
                        Statement next2 = listProperties.next();
                        System.out.println(next2);
                    }

                }
            }
        }

        return null;
    }

    protected boolean hasSameAs(String uri) {
        Resource resource = this.ontologyManager.getOntologyModel().getResource(uri);
        if (resource != null && resource.hasProperty(OWL.sameAs)) {
            return true;
        }
        return false;
    }

    @Deprecated
    protected OntModel createOntologyModel() {
        return this.ontologyManager.getOntologyModel();
    }

    @Deprecated
    protected JsonObject readConfigMapping() {
        // TODO Auto-generated method stub
        return null;
    }

    public void setOntologyManager(OntologyManager ontologyManager) {
        this.ontologyManager = ontologyManager;
    }

}
