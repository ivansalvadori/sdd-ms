package br.ufsc.inf.lapesd.sddms;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import br.ufsc.inf.lapesd.csv2rdf.CsvReader;
import br.ufsc.inf.lapesd.csv2rdf.CsvReaderListener;
import br.ufsc.inf.lapesd.sddms.database.DataBase;

@Component
public class DataManager {

    @Autowired
    private DataBase dataBase;

    public void readAndStore() {
        dataBase.resetDataset();

        CsvReader reader = new CsvReader();
        reader.setWriteToFile(false);
        CsvReaderListener listener = (CsvReaderListener) dataBase;
        reader.register(listener);
        reader.process();
    }

    public String getResourcePrefix() {
        return readConfigMapping().get("prefix").getAsString();
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

}
