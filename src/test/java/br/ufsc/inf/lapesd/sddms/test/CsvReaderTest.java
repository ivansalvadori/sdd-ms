package br.ufsc.inf.lapesd.sddms.test;

import java.io.IOException;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.junit.Test;

import com.github.jsonldjava.core.JsonLdError;

import br.ufsc.inf.lapesd.sddms.database.TDBDataBase;
import br.ufsc.inf.lapesd.sddms.reader.CsvReader;

public class CsvReaderTest {

    @Test
    public void mustReadFiles() throws IOException, JsonLdError {
        String directory = "C:\\Development\\tdb-boletins";
        TDBDataBase dataBase = new TDBDataBase(directory);
        CsvReader reader = new CsvReader();
        reader.setDataBase(dataBase);
        String pathToFiles = this.getClass().getResource("/csvReaderTest/datasetCollection2").getFile();
        String ontologyFilepath = "C:\\Development\\eclipse-workspace\\sdd-ms\\src\\test\\resources\\csvReaderTest\\ontology.owl";
        reader.setOntologyFile(ontologyFilepath);
        reader.readAndStore(pathToFiles);
    }

    @Test
    public void loadAllClasses() throws IOException {
        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb-boletins";
        TDBDataBase dataBase = new TDBDataBase(directory);
        String ontologyFilepath = "C:\\Development\\eclipse-workspace\\sdd-ms\\src\\test\\resources\\csvReaderTest\\ontology.owl";
        dataBase.setOntologyFile(ontologyFilepath);

        List<String> semanticClasses = dataBase.listAllClasses();
        for (String string : semanticClasses) {
            System.out.println(string);
        }
    }

    @Test
    public void loadBoletim() throws IOException {
        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb-boletins";
        TDBDataBase dataBase = new TDBDataBase(directory);
        String ontologyFilepath = "C:\\Development\\eclipse-workspace\\sdd-ms\\src\\test\\resources\\csvReaderTest\\ontology.owl";
        dataBase.setOntologyFile(ontologyFilepath);
        Model load = dataBase.load("sddms-resource:559B4006499E34A5749091D7C280F16DE32D4092");
        load.write(System.out, Lang.JSONLD.getName());
    }

}
