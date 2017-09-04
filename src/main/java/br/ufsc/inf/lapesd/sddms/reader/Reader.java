package br.ufsc.inf.lapesd.sddms.reader;

public interface Reader {

    void readAndStore(String pathToFiles);

    int getRecordsProcessed();

}
