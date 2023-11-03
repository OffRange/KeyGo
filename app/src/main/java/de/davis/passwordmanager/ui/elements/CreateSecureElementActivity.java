package de.davis.passwordmanager.ui.elements;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.element.SecureElement;

public abstract class CreateSecureElementActivity extends SEViewActivity {

    private static final String ELEMENT = "element";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_baseline_close_24);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(SecureElement.getTypeName(getSecureElementType()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.continue_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.menu_continue){
            Result result = check();
            if(result.isSuccess()){
                setResult(RESULT_OK, new Intent().putExtra(Keys.KEY_NEW, result.getElement()));
                finish();
            }
        }else if(item.getItemId() == android.R.id.home){
            getOnBackPressedDispatcher().onBackPressed();
        }

        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(ELEMENT, toElement());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        SecureElement element = savedInstanceState.getParcelable(ELEMENT);
        if(element == null)
            return;

        fillInElement(element);
    }

    @Override
    public void fillInElement(@NonNull SecureElement secureElement) {
        setTitle(secureElement.getTypeName());
    }

    @SecureElement.ElementType
    public abstract int getSecureElementType();

    public abstract Result check();

    protected abstract SecureElement toElement();

    public static class Result {

        private boolean success;
        private SecureElement element;

        public Result() {}

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public SecureElement getElement() {
            return element;
        }

        public void setElement(SecureElement element) {
            this.element = element;
        }
    }
}
