package br.ufsc.inf.lapesd.sddms.reader;

public interface Reader {

    void setOntologyFile(String ontologyFile);

    void readAndStore(String pathToFiles);

    int getRecordsProcessed();

}
