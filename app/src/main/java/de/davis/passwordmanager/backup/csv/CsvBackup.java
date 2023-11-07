package de.davis.passwordmanager.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.validators.RowFunctionValidator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.stream.Collectors;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.backup.DataBackup;
import de.davis.passwordmanager.backup.Result;
import de.davis.passwordmanager.database.KeyGoDatabase;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.security.element.password.PasswordDetails;

public class CsvBackup extends DataBackup {


    public CsvBackup(Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected Result runImport(InputStream inputStream) throws Exception {
        CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(inputStream))
                .withSkipLines(1)
                .withRowValidator(new RowFunctionValidator(s -> s.length == 5, getContext().getString(R.string.csv_row_number_error)))
                .withRowValidator(new RowFunctionValidator(s -> s.length == 5, getContext().getString(R.string.csv_row_number_error)))
                .build();

        String[] line;

        List<SecureElement> elements = KeyGoDatabase.getInstance().secureElementDao()
                .getAllByType(SecureElement.TYPE_PASSWORD)
                .blockingGet();

        int existed = 0;
        while ((line = csvReader.readNext()) != null) {
            if(line[0].isEmpty() || line[3].isEmpty()) // name and password must not be empty
                continue;

            String title = line[0];
            String origin = line[1];
            String username = line[2];
            String pwd = line[3];
            if(elements.stream().anyMatch(element -> element.getTitle().equals(title)
                    && ((PasswordDetails)element.getDetail()).getPassword().equals(pwd)
                    && ((PasswordDetails)element.getDetail()).getUsername().equals(username)
                    && ((PasswordDetails)element.getDetail()).getOrigin().equals(origin))) {
                existed++;
                continue;
            }

            PasswordDetails details = new PasswordDetails(pwd, origin, username);
            SecureElementManager.getInstance().createElement(new SecureElement(details, title));
        }

        csvReader.close();

        if(existed != 0)
            return new Result.Duplicate(existed);

        return new Result.Success(TYPE_IMPORT);
    }

    @NonNull
    @Override
    protected Result runExport(OutputStream outputStream) throws Exception {
        CSVWriter csvWriter = (CSVWriter) new CSVWriterBuilder(new OutputStreamWriter(outputStream))
                .build();

        List<SecureElement> elements = KeyGoDatabase.getInstance().secureElementDao()
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

        return new Result.Success(TYPE_EXPORT);
    }
}
