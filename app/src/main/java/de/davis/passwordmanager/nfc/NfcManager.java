package de.davis.passwordmanager.nfc;

import android.app.Activity;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.github.devnied.emvnfccard.exception.CommunicationException;
import com.github.devnied.emvnfccard.model.EmvCard;
import com.github.devnied.emvnfccard.parser.EmvTemplate;
import com.github.devnied.emvnfccard.parser.IProvider;

import java.io.IOException;

public abstract class NfcManager {

    private final NfcAdapter adapter;
    private final Activity activity;

    public NfcManager(Activity activity) {
        this.adapter = NfcAdapter.getDefaultAdapter(activity);
        this.activity = activity;
    }

    public boolean isAvailable(){
        return adapter != null;
    }

    public boolean isEnabled(){
         return isAvailable() && adapter.isEnabled();
    }

    public void enable(){
        if(!isAvailable())
            return;

        adapter.enableReaderMode(activity, tag -> {
            EmvCard card = null;
            CommunicationException exception = null;
            try {
                card = handleTag(tag);
            } catch (CommunicationException e) {
                exception = e;
            }finally {
                Vibrator vibrator;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    VibratorManager vibratorManager = (VibratorManager) activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    vibrator = vibratorManager.getDefaultVibrator();
                }else
                    vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                }else
                    vibrator.vibrate(100);

                EmvCard finalCard = card;
                CommunicationException finalException = exception;
                activity.runOnUiThread(() -> cardReceived(finalCard, finalException));
            }
        }, NfcAdapter.FLAG_READER_NFC_A |
                NfcAdapter.FLAG_READER_NFC_B |
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null);
    }

    public void disable(){
        if(!isAvailable())
            return;

        adapter.disableReaderMode(activity);
    }

    private EmvCard handleTag(Tag tag) throws CommunicationException{
        IsoDep isoDep = IsoDep.get(tag);
        try {
            isoDep.connect();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        IProvider provider = new Provider(isoDep);
        EmvTemplate.Config config = EmvTemplate.Config()
                .setContactLess(true)
                .setReadAllAids(true)
                .setReadTransactions(true)
                .setReadCplc(true)
                .setRemoveDefaultParsers(false)
                .setReadAt(true);

        EmvTemplate parser = EmvTemplate.Builder()
                .setProvider(provider)
                .setConfig(config)
                .build();

        return parser.readEmvCard();
    }

    protected abstract void cardReceived(EmvCard card, CommunicationException e);
}
