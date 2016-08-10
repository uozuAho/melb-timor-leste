package org.rhok.linguist.activity.common;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.koushikdutta.ion.Response;

import net.servicestack.func.Func;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;
import org.rhok.linguist.R;
import org.rhok.linguist.api.InsertInterviewRequest;
import org.rhok.linguist.api.models.Interview;
import org.rhok.linguist.api.models.Interviewee;
import org.rhok.linguist.api.models.Recording;
import org.rhok.linguist.application.LinguistApplication;
import org.rhok.linguist.code.DatabaseHelper;
import org.rhok.linguist.code.DiskSpace;
import org.rhok.linguist.code.entity.Person;
import org.rhok.linguist.code.entity.PersonWord;
import org.rhok.linguist.network.BaseIonCallback;
import org.rhok.linguist.network.IonHelper;
import org.rhok.linguist.util.StringUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;


public class UploadInterviewsActivity extends ActionBarActivity {

    private TextView uploadProgressTextView;
    private Button uploadFileButton;
    private String progressText;
    private TextView dataInfo;
    private IonHelper ionHelper;
    private List<Interview> interviewsToUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_interviews);

        uploadFileButton = (Button)findViewById(R.id.upload_file_button);
        uploadProgressTextView = (TextView)findViewById(R.id.upload_progress);
        dataInfo = (TextView)findViewById(R.id.data_info);

        DatabaseHelper db = new DatabaseHelper(getApplicationContext());
       interviewsToUpload = db.getInterviews(true);

        String numberOfPeopleText = getString(R.string.upload_interviews_format, interviewsToUpload.size());
        dataInfo.setText(numberOfPeopleText);

        progressText = "";
        ionHelper = new IonHelper(new IonHelper.IonHelperCallbacks() {
            @Override
            public void onIonRequestPreExecute(IonHelper.HelperRequest<?> request) {

            }

            @Override
            public void onIonRequestStarting(IonHelper.HelperRequest<?> request) {

            }

            @Override
            public void onIonRequestFinished(IonHelper.HelperRequest<?> request, Response<?> response) {

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_upload, menu);
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


    private InsertInterviewRequest makeInsertInterviewRequest(Interview interview){
        InsertInterviewRequest req = new InsertInterviewRequest();

        DatabaseHelper db = new DatabaseHelper(this);
        if(interview.get__intervieweeid()>0) {
            Person interviewee = db.getPerson(interview.get__intervieweeid());
            if(interviewee!=null)
                req.interviewee = new Interviewee(interviewee);
        }
        req.interview=interview;
        return req;
    }

    /**
     * called from onClick
     * @param view
     */
    public void uploadToServer(android.view.View view) {
        if(mUploading){
            Toast.makeText(this, "Upload already in progress", Toast.LENGTH_SHORT).show();
        }
        else {
            processMediaFiles(interviewsToUpload);
        }
    }
    private boolean mUploading;
    private void processMediaFiles(final List<Interview> interviews){
        mUploading=true;
        addMessage(getResources().getString(R.string.upload_starting_upload_format, interviews.size()));
        addMessage(getResources().getString(R.string.upload_uploading_data) + "...");
        new AsyncTask<Interview, Interview, Void>(){

            @Override
            protected Void doInBackground(Interview... params) {
                for (int i = 0; i < params.length; i++) {
                    Interview interview = params[i];
                    //upload any media in this thread
                    uploadMediaForInterview(interview);
                    //then upload json response using ion
                    publishProgress(interview);
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Interview... values) {
                //as each interview's media is done, upload the json interview response
                final Interview interview = values[0]; // always only one interview here, AsyncTask is weird.
                final int index = interviews.indexOf(interview);
                final boolean lastItem = index==interviews.size()-1;
                if (!interview.is__uploaded()) {

                    InsertInterviewRequest req = makeInsertInterviewRequest(interview);
                    ionHelper.doPost(ionHelper.getIon().build(UploadInterviewsActivity.this), req,  "interviews/upload")
                            .go()
                            .setCallback(new BaseIonCallback<Interview>() {
                                @Override
                                public void onSuccess(Interview result) {
                                    interview.set__uploaded(true);
                                    DatabaseHelper db = new DatabaseHelper(UploadInterviewsActivity.this);
                                    db.insertUpdateInterview(interview);

                                    addMessage(String.format("Uploaded %d/%d interviews", index + 1, interviews.size()));
                                    if(lastItem){
                                        addMessage(getResources().getString(R.string.upload_upload_complete));
                                        mUploading=false;
                                    }

                                }
                            });
                }
                else if (lastItem){
                    addMessage(getResources().getString(R.string.upload_upload_complete));
                    mUploading=false;

                }

            }

            @Override
            protected void onPostExecute(Void aVoid) {
                //image upload complete.
                //json upload not necessarily complete (done in different thread)

            }
        }.execute(Func.toArray(interviews, Interview.class));
    }

    private void sendNotification(String message) {

        Intent resultIntent = new Intent(this, UploadInterviewsActivity.class);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.livru_timor_logo)
                        .setContentTitle("Local Linguist")
                        .setContentText(message);

        // notification click behaviour
        mBuilder.setContentIntent(resultPendingIntent);

        int mNotificationId = 001;
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    private void addMessage(String message) {
        Date now = new Date();
        String thetime = DateFormat.getTimeInstance().format(now);
        progressText += thetime + " " + message + "\n";
        uploadProgressTextView.post(new Runnable() {
            public void run() {
                uploadProgressTextView.setText(progressText);
            }
        });

    }


    private void uploadMediaForInterview(Interview interview) {
        int mediaCount=0;
        for(Recording recording : interview.getRecordings()){
            //has filename, doesn't have url
            if(!StringUtils.isNullOrEmpty(recording.get__audio_filename()) && StringUtils.isNullOrEmpty(recording.getAudio_url())){
                String basePath = DiskSpace.getAudioFileBasePath();
                File f = new File(basePath + recording.get__audio_filename());

                if (f.exists()&&f.length()>0) {
                    String msg = getResources().getString(R.string.upload_uploading_audio);
                        addMessage(msg + ": " + recording.get__audio_filename());
                    String response = doFileUpload(f, recording.get__audio_filename());
                    if(response!=null && response.startsWith("http")){
                        //the url where the upload went to
                        recording.setAudio_url(response);
                        mediaCount++;
                    }
                }
            }
        }
        if(mediaCount>0){
            //save the uploaded audio urls to local db
            DatabaseHelper db = new DatabaseHelper(UploadInterviewsActivity.this);
            db.insertUpdateInterview(interview);

        }
    }




    private String doFileUpload(File file, String shortName){
        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        DataInputStream inStream = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary =  "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1*1024*1024;
        String responseFromServer = "";
        String urlString = LinguistApplication.getWebserviceUrl() + "/recordings";

        try
        {

            //------------------ CLIENT REQUEST
            FileInputStream fileInputStream = new FileInputStream(file);
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);
            conn.setRequestProperty("uploaded_file", shortName);
            dos = new DataOutputStream( conn.getOutputStream() );
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\"" + shortName + "\"" + lineEnd);
            dos.writeBytes(lineEnd);
            // create a buffer of maximum size
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];
            // read file and write it into form...
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            while (bytesRead > 0)
            {
                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }
            // send multipart form data necesssary after file data...
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            // close streams
            Log.i("LanguageApp", "File is written");
            fileInputStream.close();

            dos.flush();
            dos.close();

        }
        catch (MalformedURLException ex)
        {
            Log.e("LanguageApp", "error: " + ex.getMessage(), ex);
        }
        catch (IOException ioe)
        {
            Log.e("LanguageApp", "error: " + ioe.getMessage(), ioe);
        }


        //------------------ read the SERVER RESPONSE
        try {
            inStream = new DataInputStream ( conn.getInputStream() );
            int responseCode = conn.getResponseCode();
            Log.i("LanguageApp","Server Response code: "+responseCode);

            if(responseCode<400){
                //success. delete file.
                Log.i("LanguageApp","success. deleting file "+file.getName());
                file.delete();
            }
            String str;

            while (( str = inStream.readUTF()) != null)
            {
                Log.i("LanguageApp","Server Response: "+str);
                JSONObject jsonObj = new JSONObject(str);
                String url = jsonObj.getString("audio_file_name");
                return url;
            }
            inStream.close();

        }
        catch (IOException|JSONException ioex){
            Log.e("LanguageApp", "error: " + ioex.getMessage(), ioex);
        }
        return null;
    }


}
