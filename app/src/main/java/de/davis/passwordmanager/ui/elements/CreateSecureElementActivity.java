package de.davis.passwordmanager.ui.elements;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.element.SecureElement;

public abstract class CreateSecureElementActivity extends SecureElementActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_baseline_close_24);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
            onBackPressed();
        }

        return true;
    }

    public abstract Result check();

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
