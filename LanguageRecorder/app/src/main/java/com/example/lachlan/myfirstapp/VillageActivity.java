package com.example.lachlan.myfirstapp;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import com.example.lachlan.myfirstapp.R;
import com.example.lachlan.myfirstapp.code.Location;
import com.example.lachlan.myfirstapp.code.SubDistrict;
import com.example.lachlan.myfirstapp.code.Village;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VillageActivity extends ActionBarActivity {
    private String selectedVillage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Select village for " + selectedSubDistrict());
        setContentView(R.layout.activity_village);
        populateVillages();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_village, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void populateVillages() {
        ListView lvVillages = (ListView) findViewById(R.id.village_list);
        ArrayList<String> list = new ArrayList<String>();
        for (Village v : villages()) {
            if (!list.contains(v.name)) {
                list.add(v.name);
            }
        }
        Collections.sort(list);
        ArrayAdapter<String> aaSubDistricts = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, list);
        aaSubDistricts
                .setDropDownViewResource(android.R.layout.simple_expandable_list_item_1);
        lvVillages.setAdapter(aaSubDistricts);
        lvVillages.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedVillage = (String) parent.getItemAtPosition(position);
            }
        });
    }

    private ArrayList<Village> villages() {
        Location l;
        ArrayList<Village> m = new ArrayList<>();
        for (String loc : locations()) {
            l = new Location(loc);
            if (l.subdistrict.equals(selectedSubDistrict())) {
                m.add(new Village(this, l.municipality, l.subdistrict, l.village));
            }
        }
        return m;
    }

    public void loadVillageActivity(android.view.View view) {
        Intent intent = new Intent(this, VillageActivity.class);
        intent.putExtra("VILLAGE", selectedVillage);
        startActivity(intent);
    }

    private String selectedSubDistrict() {
        Bundle extras = getIntent().getExtras();
        String value = "";
        if (extras != null) {
            value = extras.getString("SUB_DISTRICT");
        }
        return value;
    }

    private String[] locations() {
        return getResources().getStringArray(R.array.locations);
    }
}
