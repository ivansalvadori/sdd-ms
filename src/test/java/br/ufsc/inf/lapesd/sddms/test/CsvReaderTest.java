package br.ufsc.inf.lapesd.sddms.test;

import java.io.IOException;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.junit.Test;

import com.github.jsonldjava.core.JsonLdError;

import br.ufsc.inf.lapesd.sddms.DataBase;
import br.ufsc.inf.lapesd.sddms.reader.CsvReader;

public class CsvReaderTest {

    @Test
    public void mustReadFiles() throws IOException, JsonLdError {
        String directory = "C:\\Development\\tdb-boletins";
        DataBase dataBase = new DataBase(directory);
        CsvReader reader = new CsvReader();
        reader.setDataBase(dataBase);
        String pathToFiles = this.getClass().getResource("/csvReaderTest/datasetCollection2").getFile();
        reader.readAndStore(pathToFiles);
    }

    @Test
    public void loadAllClasses() throws IOException {
        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb-boletins";
        DataBase dataBase = new DataBase(directory);
        List<String> semanticClasses = dataBase.listAllClasses();
        for (String string : semanticClasses) {
            System.out.println(string);
        }
    }

    @Test
    public void loadAllBoletins() throws IOException {
        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb-boletins";
        DataBase dataBase = new DataBase(directory);
        List<String> listAllResources = dataBase.listAllResources("http://www.public-security-ontology/BoletimOcorrencia");
        for (String string : listAllResources) {
            System.out.println(string);
        }
    }

    @Test
    public void loadBoletim() throws IOException {
        // Make a TDB-backed dataset
        String directory = "C:\\Development\\tdb-boletins";
        DataBase dataBase = new DataBase(directory);
        Model load = dataBase.load("sddms-resource:A10FF43DCE3B4E99FA0B2EA248734831BCB0092C");
        load.write(System.out, Lang.JSONLD.getName());
    }

}
