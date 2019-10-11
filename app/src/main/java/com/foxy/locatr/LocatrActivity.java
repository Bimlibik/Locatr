package com.foxy.locatr;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;

public class LocatrActivity extends SingleFragmentActivity {

    @Override
    protected Fragment createFragment() {
        return LocatrFragment.newInstance();
    }
}
