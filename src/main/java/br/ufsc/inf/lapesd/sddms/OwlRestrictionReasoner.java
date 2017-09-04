package br.ufsc.inf.lapesd.sddms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.util.iterator.ExtendedIterator;

public class OwlRestrictionReasoner {

    private InfModel pModel;
    private OntModel ontologyModel;
    private Dataset dataset;

    public void init(String tdbDirectory, OntModel ontologyModel) throws IOException {
        String directory = "C:\\Development\\tdb";
        dataset = TDBFactory.createDataset(directory);
        dataset.begin(ReadWrite.READ);
        Model defaultModel = dataset.getDefaultModel();
        pModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF, defaultModel);
        this.ontologyModel = ontologyModel;
    }

    public List<String> listSemanticClasses() {
        List<String> classes = new ArrayList<>();
        ExtendedIterator<OntClass> listClasses = ontologyModel.listClasses();
        while (listClasses.hasNext()) {
            OntClass next = listClasses.next();
            if (next.getURI() != null) {
                classes.add(next.getURI());
            }
        }
        return classes;
    }

    public List<Resource> listAllIndividuals(String semanticClass) {
        List<Resource> individuals = new ArrayList<>();
        ResIterator listSubjects = pModel.listSubjects();
        while (listSubjects.hasNext()) {
            Resource next = listSubjects.next();
            Resource individual = pModel.getResource(next.getURI());
            StmtIterator listProperties = individual.listProperties();
            while (listProperties.hasNext()) {
                Statement property = listProperties.next();
                Property predicate = property.getPredicate();
                RDFNode object = property.getObject();
                if (predicate.toString().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") && object.toString().equals(semanticClass)) {
                    individuals.add(individual);
                }
            }
        }
        return individuals;
    }

    public void queryTDB() {
        StringBuilder queryStr = new StringBuilder();
        queryStr.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ");
        queryStr.append("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ");
        queryStr.append("PREFIX api: <http://api.com#> ");
        queryStr.append("SELECT ?resource ");
        queryStr.append("WHERE { ");
        queryStr.append("?resource api:idade ?idade . FILTER( ?idade > \"20\" ) .  ");
        queryStr.append("?resource api:idade ?idade . FILTER( ?idade > \"40\" ) .  ");

        queryStr.append("} ");

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, pModel);
        /* Execute the Query */
        ResultSet results = qexec.execSelect();
        ResultSetFormatter.out(results);
        // ResultSetFormatter.asText(results);
        qexec.close();
    }

}
