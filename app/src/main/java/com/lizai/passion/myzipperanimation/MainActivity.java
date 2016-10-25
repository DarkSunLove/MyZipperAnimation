package com.lizai.passion.myzipperanimation;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.lizai.passion.myzipperanimation.view.ZipperView;

public class MainActivity extends AppCompatActivity implements ZipperView.OnZipperOpenedListener {

    private ZipperView mZipperView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mZipperView = (ZipperView) findViewById(R.id.page_widget);
        mZipperView.setOnZipperOpenedListener(this);
    }

    @Override
    public void onZipperOpened() {
        Toast.makeText(this,"打开",Toast.LENGTH_SHORT).show();
    }
}
