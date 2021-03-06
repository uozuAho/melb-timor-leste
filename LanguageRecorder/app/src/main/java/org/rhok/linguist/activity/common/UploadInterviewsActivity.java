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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.koushikdutta.ion.Response;

import net.servicestack.client.JsonSerializers;
import net.servicestack.client.TimeSpan;
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
import org.rhok.linguist.code.PreferencesHelper;
import org.rhok.linguist.code.entity.Person;
import org.rhok.linguist.code.entity.PersonWord;
import org.rhok.linguist.network.BaseIonCallback;
import org.rhok.linguist.network.IonHelper;
import org.rhok.linguist.network.PCJsonSerializers;
import org.rhok.linguist.util.StringUtils;
import org.rhok.linguist.util.ZipUtil;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLStreamHandler;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;


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
        req.interviewer = PreferencesHelper.getInterviewer();
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
            processMediaFilesToZip(interviewsToUpload);
        }
    }

    private void processMediaFilesToZip(final List<Interview> interviews){
        mUploading=true;
        addMessage(getResources().getString(R.string.upload_starting_upload_format, interviews.size()));
        addMessage(getResources().getString(R.string.upload_uploading_data) + "...");
        new AsyncTask<Interview, Interview, Void>(){

            @Override
            protected Void doInBackground(Interview... params) {
                for (int i = 0; i < params.length; i++) {
                    Interview interview = params[i];

                    if(!interview.is__uploaded()){

                        List<File> mediaFiles = new ArrayList<File>();
                        for(Recording recording : interview.getRecordings()){
                            //has filename
                            if(!StringUtils.isNullOrEmpty(recording.get__audio_filename()) ){
                                File f = DiskSpace.getInterviewRecording(recording.get__audio_filename());

                                if (f.exists()&&f.length()>0) {
                                    addMessage("compressing: " + recording.get__audio_filename());
                                    mediaFiles.add(f);
                                    recording.setAudio_url(f.getName());
                                }
                            }
                        }
                        InsertInterviewRequest req = makeInsertInterviewRequest(interview);
                        //instead of using default IonHelper's gson, make our own here with pretty printing
                        //Gson gson = ionHelper.getIon().configure().getGson();

                        GsonBuilder gsonBuilder = new GsonBuilder()
                                .registerTypeAdapter(Date.class, JsonSerializers.getDateSerializer())
                                .registerTypeAdapter(Date.class, PCJsonSerializers.getDateDeserializer())
                                .setPrettyPrinting();
                        gsonBuilder.setExclusionStrategies(PCJsonSerializers.getUnderscoreExclusionStrategy());
                        Gson gson = gsonBuilder.create();

                        String json = gson.toJson(req);
                        HashMap<String, String> mapOfTextFileNameBody = Func.toDictionary("upload.json", json);
                        String destinationFileName = String.format("study_%d_response_%d.zip", interview.getStudy_id(), System.currentTimeMillis());
                        File destinationFile = new File(DiskSpace.getInterviewsPath(), destinationFileName);
                        try {
                            ZipUtil.zip(mediaFiles, mapOfTextFileNameBody, destinationFile);
                        } catch (IOException e) {
                            addMessage("Error compressing files: "+e.getMessage());
                            e.printStackTrace();
                        }
                        if(destinationFile.exists()){
                            addMessage("Wrote zip to " + destinationFile.getAbsolutePath());
                            //TODO upload the zip

                            List<String> msgs = new ArrayList<String>();
                            msgs.add("`Interview date:` "+StringUtils.formatDate(req.interview.getInterview_time(), StringUtils.DATE_AND_TIME_STANDARD));
                            msgs.add(String.format("`StudyId:` %d (%d responses)", req.interview.getStudy_id(), req.interview.getRecordings().size()));
                            if(req.interviewer!=null){
                                msgs.add("`Interviewer:` "+req.interviewer.getName());
                            }
                            if(req.interviewee!=null){
                                msgs.add("`Interviewee:` "+req.interviewee.getName());
                            }
                            String slackMsg = null;
                            try {
                                slackMsg = URLEncoder.encode(StringUtils.stringListToString(msgs, "\n", false), "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                            //If you get a build error here, create a file res/values/api_keys.xml
                            /*String slackToken = getString(R.string.slack_bot_token);
                            String slackChannel =getString(R.string.slack_channel);
                            String url = "https://slack.com/api/files.upload?token="+slackToken+"&channels="+slackChannel+"&initial_comment="+slackMsg;
                            */
                            //doFileUpload(url, destinationFile, destinationFileName);
                            doFileUpload("api/v1/upload_interview", destinationFile, destinationFileName, interview);
                        }
                    }
                }
                return null;
            }



            @Override
            protected void onPostExecute(Void aVoid) {
                //zip compression complete

                mUploading=false;

            }
        }.execute(Func.toArray(interviews, Interview.class));
    }


    private boolean mUploading;

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

    /**
     * Upload a single zipped interview
     */
    private String doFileUpload(String urlPath, File file, String shortName, Interview interview){
        DatabaseHelper db = new DatabaseHelper(this);
        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        BufferedReader inStream = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary =  "************";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        final int maxBufferSize = 1*1024*1024;
        String responseFromServer = "";
        String urlString;
        if(urlPath.startsWith("http")){
            urlString = urlPath;
        }
        else {
            urlString = LinguistApplication.getWebserviceUrl();
            if (!urlString.endsWith("/")) urlString += "/";
            urlString += urlPath;
        }
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
            //conn.setRequestProperty("Connection", "Keep-Alive");
            //conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data; charset=utf-8; boundary="+boundary);
            //conn.setRequestProperty("uploaded_file", shortName);
            dos = new DataOutputStream( conn.getOutputStream() );
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            //writeInterviewMultipartFormFields(interview, dos, boundary);
            //dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + shortName + "\"" + lineEnd);
            dos.writeBytes("Content-Type: application/zip" + lineEnd);
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

            int responseCode = conn.getResponseCode();
            if (200 <= conn.getResponseCode() && conn.getResponseCode() <= 299) {
                inStream = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            } else {
                inStream = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
            }
            Log.i("LanguageApp","Server Response code: "+responseCode);

            if(responseCode<400){
                //success. delete file & mark interview as uploaded
                Log.i("LanguageApp","success. deleting file "+file.getName());
                file.delete();
                interview.set__uploaded(true);
                db.insertUpdateInterview(interview);
                addMessage("Upload successful");
            }
            else {
                addMessage("Upload failed");
            }
            String str;
            StringBuilder sb = new StringBuilder();
            String output;
            while ((output = inStream.readLine()) != null) {
                sb.append(output);
            }
            inStream.close();
            str = sb.toString();

                Log.i("LanguageApp","Server Response: "+str);
                JSONObject jsonObj = new JSONObject(str);
            String url = jsonObj.optString("audio_file_name");
                return url;

            //

        }
        catch (IOException|JSONException ioex){
            Log.e("LanguageApp", "error: " + ioex.getMessage(), ioex);
        }
        return null;
    }

    private static void writeInterviewMultipartFormFields (
            Interview interview, DataOutputStream stream, String boundary) throws IOException {
        String newline = "\r\n";
        writeInterviewMultipartFormField("study_id", interview.getStudy_id(), stream);
        stream.writeBytes("--" + boundary + newline);
        // interview locale id is not populated?
        // writeInterviewMultipartFormField("locale_id", interview.getLocale_id(), stream);
        // just write 1:
        writeInterviewMultipartFormField("locale_id", 1, stream);
    }

    private static void writeInterviewMultipartFormField(
            String fieldname, int field, DataOutputStream stream) throws IOException {
        String newline = "\r\n";
        stream.writeBytes("Content-Disposition: form-data; name=\"interview[" + fieldname + "]\"" + newline);
        stream.writeBytes(newline);
        stream.writeBytes(Integer.toString(field) + newline);
    }


}
