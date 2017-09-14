package br.ufsc.inf.lapesd.sddms;

import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.csv2rdf.CsvReader;
import br.ufsc.inf.lapesd.csv2rdf.CsvReaderListener;
import br.ufsc.inf.lapesd.sddms.database.DataBase;

@Component
public class DataManager {

    @Autowired
    private DataBase dataBase;

    private CsvReader reader = new CsvReader();

    public void readAndStore() {
        reader.setWriteToFile(false);
        CsvReaderListener listener = (CsvReaderListener) dataBase;
        reader.register(listener);
        reader.process();
    }

    public String getResourcePrefix() {
        return this.reader.getPrefix();
    }

    public void resetDataset() {
        this.dataBase.resetDataset();
    }

    public List<String> getAllManagedSemanticClasses() {
        return dataBase.listAllClasses();
    }

    public Model getResource(String uri) {
        return dataBase.load(uri);
    }

    public Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues) {
        return dataBase.queryTDB(rdfType, propertiesAndvalues);
    }

}
