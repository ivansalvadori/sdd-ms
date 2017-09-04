package br.ufsc.inf.lapesd.sddms;

import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.sddms.reader.Reader;

@Component
public class DataManager {

    @Autowired
    private DataBase dataBase;

    @Autowired
    private Reader reader;

    @Value("${config.filePath}")
    private String directory;

    @Value("${config.ontologyFile}")
    private String ontologyFile;

    @PostConstruct
    public void init() {
        this.dataBase.setOntologyFile(ontologyFile);
    }

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

    public List<String> listAllResourceUris(String rdfType) {
        return dataBase.listAllResources(rdfType);
    }

    public Model getResource(String uri) {
        return dataBase.load(uri);
    }

    public Model queryTDB(String rdfType, Map<String, String> propertiesAndvalues) {
        return dataBase.queryTDB(rdfType, propertiesAndvalues);
    }

}
