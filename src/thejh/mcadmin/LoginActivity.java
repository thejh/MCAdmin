package thejh.mcadmin;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class LoginActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);
        try {
			BufferedReader r = new BufferedReader(new InputStreamReader(openFileInput("saved_credentials")));
			String host = r.readLine();
			String port = r.readLine();
			String pass = r.readLine();
	    	setETString(R.id.host, host);
	    	setETString(R.id.port, port);
	    	setETString(R.id.pass, pass);
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
    }
    
    private String getETString(int id) {
    	EditText e = (EditText) findViewById(id);
    	return e.getText().toString();
    }
    
    private void setETString(int id, String s) {
    	EditText e = (EditText) findViewById(id);
    	e.setText(s);
    }
    
    public void do_login(View view) {
    	String host = getETString(R.id.host);
    	String port = getETString(R.id.port);
    	String pass = getETString(R.id.pass);
    	try {
			FileOutputStream out = openFileOutput("saved_credentials", 0);
			OutputStreamWriter outw = new OutputStreamWriter(out);
			outw.write(host+"\n"+port+"\n"+pass);
			outw.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
    	Intent i = new Intent();
    	i.putExtra("host", host);
    	i.putExtra("port", port);
    	i.putExtra("pass", pass);
    	setResult(0, i);
    	Log.d("MC-login", "calling finish()...");
    	finish();
    }
}
