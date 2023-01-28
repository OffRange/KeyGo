package de.davis.passwordmanager.manager;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.Toast;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.views.copy.CopyView;

public class CopyManager {

    public static void toClipboard(ClipData data, Context context){
        ((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(data);
    }

    public static class Listener implements View.OnClickListener{
        @Override
        public void onClick(View v) {
            if(!(v instanceof CopyView))
                return;

            String text = ((CopyView) v).getCopyString();
            if(text.trim().isEmpty())
                return;


            toClipboard(ClipData.newPlainText(text, text), v.getContext());
            Toast.makeText(v.getContext(), R.string.copied_data, Toast.LENGTH_LONG).show();
        }
    }
}
