package org.rhok.linguist.interview;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import org.rhok.linguist.R;
import org.rhok.linguist.code.Person;

public class InterviewNameActivity extends BaseInterviewActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_interview_name);

        EditText editText = (EditText)findViewById(R.id.nameEditText);
        if (editText.requestFocus()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    public void nextButtonClick(android.view.View view) {

        EditText editText = (EditText)findViewById(R.id.nameEditText);
        String name = editText.getText().toString();

        if (name.trim().length() == 0) {
            Toast toast = Toast.makeText(getApplicationContext(), "Please enter a name", Toast.LENGTH_SHORT);
            toast.show();
        } else {

            Intent intent = new Intent(this, InterviewAgeActivity.class);
            Person p = new Person();
            p.name = name;

            intent.putExtra("person", p);
            startActivity(intent);
        }
    }
}
