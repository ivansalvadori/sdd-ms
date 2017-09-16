package br.ufsc.inf.lapesd.sddms.database;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataBaseConfig {

    @Value("${config.persistenceType}")
    private String persistenceType;

    @Bean
    public DataBase dataBaseService() {
        if (persistenceType.equalsIgnoreCase("inmemory")) {
            return new InMemoryDataBase();
        } else if (persistenceType.equalsIgnoreCase("fuseki")) {
            return new FusekiDataBase();
        } else if (persistenceType.equalsIgnoreCase("fusekiSingleModel")) {
            return new FusekiSingleModelDataBase();
        } else if (persistenceType.equalsIgnoreCase("tdbSingleModel")) {
            return new TDBSingleModelDataBase();
        } else if (persistenceType.equalsIgnoreCase("InMemoryMultipleModels")) {
            return new InMemoryMultipleModels();
        } else if (persistenceType.equalsIgnoreCase("RdfFiles")) {
            return new RdfMultipleModelsDataBase();
        } else if (persistenceType.equalsIgnoreCase("RdfFilesMultipleModelsMapDB")) {
            return new RdfMultipleModelsMapDbDataBase();
        } else {
            return new TDBMultipleModelsDataBase();
        }
    }

}