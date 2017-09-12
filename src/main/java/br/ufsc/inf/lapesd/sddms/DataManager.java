package br.ufsc.inf.lapesd.sddms;

import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.database.DataBase;
import br.ufsc.inf.lapesd.sddms.reader.Reader;

@Component
public class DataManager {

    @Autowired
    private DataBase dataBase;

    @Autowired
    private Reader reader;

    @Value("${config.filePath}")
    private String directory;

    public int getReadingStatus() {
        return this.reader.getRecordsProcessed();
    }

    public void readAndStore() {
        reader.readAndStore(directory);
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
