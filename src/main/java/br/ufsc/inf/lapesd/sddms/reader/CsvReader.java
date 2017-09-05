package br.ufsc.inf.lapesd.sddms.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import br.ufsc.inf.lapesd.sddms.DataBase;

@Component
public class CsvReader implements br.ufsc.inf.lapesd.sddms.reader.Reader {

    @Autowired
    private DataBase dataBase;

    private String ontologyFile;

    public void setDataBase(DataBase dataBase) {
        this.dataBase = dataBase;
    }

    public void setOntologyFile(String ontologyFile) {
        this.ontologyFile = ontologyFile;
    }

    private int recordsProcessed = 0;

    @Override
    public int getRecordsProcessed() {
        return recordsProcessed;
    }

    @Override
    public void readAndStore(String pathToFiles) {
        recordsProcessed = 0;
        OntModel ontologyModel = createOntologyModel();

        try {

            JsonObject mappingContext = createContextMapping(pathToFiles);
            JsonObject mappingConfing = createConfigMapping(pathToFiles);

            Collection<File> files = FileUtils.listFiles(new File(pathToFiles), null, true);
            for (File file : files) {
                if (file.getName().equals("mapping.jsonld")) {
                    continue;
                }
                System.out.println("reading " + file.getName());
                Reader in = new FileReader(file.getPath());

                Iterable<CSVRecord> records = null;

                String csvSpearator = mappingConfing.get("http://www.ivansalvadori.com.br/sdd-ms-ontology.owl#csvSeparator").getAsString();
                if (csvSpearator.equalsIgnoreCase("COMMA")) {
                    records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
                }
                if (csvSpearator.equalsIgnoreCase("TAB")) {
                    records = CSVFormat.TDF.withFirstRecordAsHeader().parse(in);
                }

                for (CSVRecord record : records) {
                    Individual resource = createResourceModel(mappingContext, mappingConfing, record, ontologyModel);
                    dataBase.store(resource.getModel());
                    System.out.println(++recordsProcessed + " records processed");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JsonObject createContextMapping(String pathToFiles) throws IOException {
        Collection<File> files = FileUtils.listFiles(new File(pathToFiles), null, true);
        for (File file : files) {
            if (file.getName().equals("mapping.jsonld")) {
                try (FileInputStream inputStream = FileUtils.openInputStream(file)) {
                    String mappingContextString = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
                    JsonObject mappingJsonObject = new JsonParser().parse(mappingContextString).getAsJsonObject();
                    return mappingJsonObject.get("@context").getAsJsonObject();
                }
            }
        }
        throw new RuntimeException("Invalid context mapping");
    }

    private JsonObject createConfigMapping(String pathToFiles) throws IOException {
        Collection<File> files = FileUtils.listFiles(new File(pathToFiles), null, true);
        for (File file : files) {
            if (file.getName().equals("mapping.jsonld")) {
                try (FileInputStream inputStream = FileUtils.openInputStream(file)) {
                    String mappingContextString = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
                    JsonObject mappingJsonObject = new JsonParser().parse(mappingContextString).getAsJsonObject();
                    return mappingJsonObject.get("http://www.ivansalvadori.com.br/sdd-ms-ontology.owl#Configuration").getAsJsonObject();
                }
            }
        }
        throw new RuntimeException("Invalid context mapping");
    }

    private Individual createResourceModel(JsonObject mappingContext, JsonObject mappingConfig, CSVRecord record, OntModel ontologyModel) {
        OntResource resourceClass = ontologyModel.createOntResource(mappingContext.get("@type").getAsString());
        String uri = createResourceUri(mappingContext, record, resourceClass.getURI());
        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        Individual individual = model.createIndividual(uri, resourceClass);

        if (!mappingContext.isJsonNull()) {
            Set<Entry<String, JsonElement>> entrySet = mappingContext.getAsJsonObject().entrySet();
            for (Entry<String, JsonElement> entry : entrySet) {
                if (entry.getKey().equals("@type") || entry.getKey().equals("@uriProperty")) {
                    continue;
                }
                if (entry.getValue().isJsonPrimitive()) {
                    DatatypeProperty property = ontologyModel.createDatatypeProperty(entry.getValue().getAsString());
                    individual.addProperty(property, record.get(entry.getKey()));
                }
                if (entry.getValue().isJsonObject()) {
                    Individual innerResource = createResourceModel(entry.getValue().getAsJsonObject(), mappingConfig, record, ontologyModel);
                    ObjectProperty property = ontologyModel.getObjectProperty(entry.getKey());
                    if (property == null) {
                        property = ontologyModel.createObjectProperty(entry.getKey());
                    }
                    individual.addProperty(property, innerResource);
                    individual.getModel().add(innerResource.getModel());
                    if (property.hasInverse()) {
                        OntProperty inverseOf = property.getInverseOf();
                        innerResource.addProperty(inverseOf, individual);
                    }
                }
            }
        }
        return individual;
    }

    private OntModel createOntologyModel() {
        String ontologyString = null;
        try {
            ontologyString = new String(Files.readAllBytes(Paths.get(ontologyFile)));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        OntModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);
        ontologyModel.read(new StringReader(ontologyString), null, "N3");
        return ontologyModel;
    }

    private String createResourceUri(JsonObject mappingContext, CSVRecord record, String resourceTypeUri) {
        String resourceUri = resourceTypeUri;

        if (mappingContext.get("@uriProperty").isJsonPrimitive()) {
            String propertyKey = mappingContext.get("@uriProperty").getAsString();
            if (propertyKey.equalsIgnoreCase("RandomUri")) {
                resourceUri = UUID.randomUUID().toString();
            } else {
                resourceUri = record.get(propertyKey);
            }
        } else if (mappingContext.get("@uriProperty").isJsonArray()) {
            JsonArray asJsonArray = mappingContext.get("@uriProperty").getAsJsonArray();
            Iterator<JsonElement> iterator = asJsonArray.iterator();
            while (iterator.hasNext()) {
                JsonElement next = iterator.next();
                if (next.isJsonPrimitive()) {
                    String propertyKey = next.getAsString();
                    resourceUri = resourceUri + record.get(propertyKey);
                }
            }
        }

        String sha1 = sha1(resourceUri);
        return "sddms-resource:" + sha1;
    }

    private String sha1(String input) {
        String sha1 = null;
        try {
            MessageDigest msdDigest = MessageDigest.getInstance("SHA-1");
            msdDigest.update(input.getBytes("UTF-8"), 0, input.length());
            sha1 = DatatypeConverter.printHexBinary(msdDigest.digest());
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            System.out.println("SHA1 error");
        }
        return sha1;
    }
}
