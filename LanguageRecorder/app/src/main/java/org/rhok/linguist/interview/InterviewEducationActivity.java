package org.rhok.linguist.interview;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import org.rhok.linguist.R;
import org.rhok.linguist.code.ListViewPopulator;
import org.rhok.linguist.code.Person;

public class InterviewEducationActivity extends BaseInterviewActivity {

    private Person _person;
    private String selectedEducation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interview_education);

        Intent intent = getIntent();
        _person = (Person) intent.getSerializableExtra("person");

        populateEducations();
    }

    private void populateEducations() {

        ListViewPopulator.populate(this, R.id.educationListView, R.array.education, false,
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        selectedEducation = (String) parent.getItemAtPosition(position);
                    }
                });
    }

    public void nextButtonClick(android.view.View view) {

        if (selectedEducation == null) {
            Toast toast = Toast.makeText(getApplicationContext(), "Please select an occupation", Toast.LENGTH_SHORT);
            toast.show();
        } else {
            _person.education = selectedEducation;

            String question = getResources().getString(R.string.interview_primarylanguage);
            Intent intent = new Intent(this, InterviewSpokenLanguageActivity.class);
            intent.putExtra("LANGUAGE_QUESTION", question);
            intent.putExtra("NEXT_ACTIVITY", "MoreLanguages");
            intent.putExtra("Person", _person);
            intent.putExtra("LanguageNumber", 1);
            startActivity(intent);
        }

    }
}