package br.ufsc.inf.lapesd.sddms.database;

import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Model;

public interface DataBase {

    void store(Model model);

    void resetDataset();

    void commit();

    List<String> listAllClasses();

    Model load(String resourceUri);

    Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues);

}
