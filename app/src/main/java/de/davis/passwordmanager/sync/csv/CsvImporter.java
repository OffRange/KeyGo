package de.davis.passwordmanager.sync.csv;

import android.content.Context;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.validators.RowFunctionValidator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.sync.Result;
import de.davis.passwordmanager.sync.Importer;

public class CsvImporter implements Importer {

    private final CSVReader csvReader;
    private final Context context;

    public CsvImporter(InputStream inputStream, Context context) {
        csvReader = new CSVReaderBuilder(new InputStreamReader(inputStream))
                .withSkipLines(1)
                .withRowValidator(new RowFunctionValidator(s -> s.length == 5, context.getString(R.string.csv_row_number_error)))
                .withRowValidator(new RowFunctionValidator(s -> s.length == 5, context.getString(R.string.csv_row_number_error)))
                .build();
        this.context = context;
    }

    @Override
    public Result importElements() throws Exception {
        String[] line;

        List<SecureElement> elements = SecureElementDatabase.getInstance()
                .getSecureElementDao()
                .getAllByType(SecureElement.TYPE_PASSWORD)
                .blockingGet();

        int existed = 0;
        while ((line = csvReader.readNext()) != null) {
            if(line[0].isEmpty() || line[3].isEmpty()) // name and password must not be empty
                continue;

            String title = line[0];
            if(elements.stream().anyMatch(element -> element.getTitle().equals(title))) {
                existed++;
                continue;
            }

            PasswordDetails details = new PasswordDetails(line[3], line[1], line[2]);
            SecureElementManager.getInstance().createElement(new SecureElement(details, title));
        }

        csvReader.close();

        if(existed != 0)
            return new Result.Error(context.getResources().getQuantityString(R.plurals.csv_item_title_existed, existed, existed));

        return new Result.Success();
    }
}
