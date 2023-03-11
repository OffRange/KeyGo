package de.davis.passwordmanager.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AssetsUtil {

    public static List<String> open(String filename, Context context) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(filename), StandardCharsets.UTF_8));
        List<String> result = new ArrayList<>();
        for (;;) {
            String line = reader.readLine();
            if (line == null)
                break;

            if(line.startsWith("#"))
                continue;

            result.add(line);
        }
        return result;
    }
}
