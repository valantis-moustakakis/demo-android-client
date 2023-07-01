package technology.moro.thesis.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import technology.moro.thesis.R;
import technology.moro.thesis.fragments.MapFragment;

public class MapActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container_view, MapFragment.class, null)
                .commit();
    }
}
