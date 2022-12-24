package de.davis.passwordmanager.nfc;

import android.nfc.tech.IsoDep;

import com.github.devnied.emvnfccard.exception.CommunicationException;
import com.github.devnied.emvnfccard.parser.IProvider;

import java.io.IOException;

public class Provider implements IProvider {

    private final IsoDep isoDep;

    public Provider(IsoDep isoDep) {
        this.isoDep = isoDep;
    }

    @Override
    public byte[] transceive(byte[] pCommand) throws CommunicationException {
        byte[] response;
        try {
            response = isoDep.transceive(pCommand);
        } catch (IOException e) {
            throw new CommunicationException(e.getMessage());
        }

        return response;
    }

    @Override
    public byte[] getAt() {
        return isoDep.getHistoricalBytes();
    }
}
