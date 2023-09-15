package de.davis.passwordmanager.sync.csv;

import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.stream.Collectors;

import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.sync.Result;
import de.davis.passwordmanager.sync.Exporter;

public class CsvExporter implements Exporter {

    private final CSVWriter csvWriter;

    public CsvExporter(OutputStream outputStream) {
        this.csvWriter = (CSVWriter) new CSVWriterBuilder(new OutputStreamWriter(outputStream))
                .build();
    }

    @Override
    public Result exportElements() throws Exception {
        List<SecureElement> elements = SecureElementDatabase.getInstance()
                .getSecureElementDao()
                .getAllByType(SecureElement.TYPE_PASSWORD)
                .blockingGet();

        csvWriter.writeNext(new String[]{"name", "url", "username", "password", "note"});


        csvWriter.writeAll(elements.stream().map(pwd -> new String[]{pwd.getTitle(),
                ((PasswordDetails)pwd.getDetail()).getOrigin(),
                ((PasswordDetails)pwd.getDetail()).getUsername(),
                ((PasswordDetails)pwd.getDetail()).getPassword(),
                null}).collect(Collectors.toList()));

        csvWriter.flush();
        csvWriter.close();

        return new Result.Success();
    }
}
