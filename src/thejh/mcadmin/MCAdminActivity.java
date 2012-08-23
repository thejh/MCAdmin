package thejh.mcadmin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class MCAdminActivity extends Activity {
	private MCAdminActivity self = this;
	private OnClickListener exit_listener = new OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			self.finish();
		}
	};
	private InputStream in;
	private OutputStream out;
	private Handler h;
	private int req_id = 0;
	private boolean dead = false;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        h = new Handler(); // TODO is posting to this queue without synchronization really ok?
        Intent i = new Intent(this, LoginActivity.class);
        startActivityForResult(i, 1);
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	try {
    		dead = true;
			in.close();
	    	out.close();
		} catch (IOException e) {}
    }
    
    private void terminate(String err) {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Could not login:\n"+err);
		builder.setCancelable(false);
		builder.setNeutralButton("exit", exit_listener);
		builder.create().show();
    }
    
    private int send_message(byte type, String payload) throws IOException {
    	byte[] payload_bytes = payload.getBytes(Charset.forName("UTF-8"));
    	byte[] data = new byte[4+4+4+payload_bytes.length+2];
    	int remainder_length = 4+4+payload_bytes.length+2;
    	int i=0;
    	for (int j=0; j<4; j++) {
    		data[i++] = (byte) (remainder_length % 256);
    		remainder_length /= 256;
    	}
    	int req_id_clone = req_id;
    	for (int j=0; j<4; j++) {
    		data[i++] = (byte) (req_id_clone % 256);
    		req_id_clone /= 256;
    	}
    	data[i++] = type;
    	data[i++] = 0;
    	data[i++] = 0;
    	data[i++] = 0;
    	System.arraycopy(payload_bytes, 0, data, i, payload_bytes.length);
    	i += payload_bytes.length;
    	data[i++] = 0;
    	data[i++] = 0;
    	out.write(data);
    	return req_id++;
    }
    
    private void send_message(final String message) {
    	new Thread() {
    		@Override
    		public void run() {
    			try {
					send_message((byte)2, message);
				} catch (final IOException e) {
					h.post(new Runnable() {
						public void run() {
							terminate(e.getLocalizedMessage());
						}
					});
				}
    		}
    	}.start();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	Log.d("MC", "got result, req="+requestCode+" res="+resultCode);
    	if (requestCode == 1) {
    		final String host = data.getStringExtra("host");
    		String sport = data.getStringExtra("port");
    		final String pass = data.getStringExtra("pass");
    		if (resultCode != 0 || host==null || sport==null || pass==null) {
    			finish();
    			return;
    		}
    		if (sport.equals("")) sport = "25575";
    		final int port = Integer.parseInt(sport, 10);

    		new Thread() {
    			public void run() {
    				try {
    					Socket s = new Socket(host, port);
    					in = s.getInputStream();
    					out = s.getOutputStream();
    					new ChatReceiver();
    					send_message((byte)3, pass);
    				} catch (final IOException e) {
    					h.post(new Runnable() {
    						public void run() {
    							terminate(e.getLocalizedMessage());
    						}
    					});
    				}
    			}
    		}.start();
    	}
    }
    
    private boolean logged_in = false;
    private void handle_packet(int req_id, int type, String payload) {
    	Log.d("MC", "handling packet, req="+req_id+" type="+type+" payloadlen="+payload.length());
    	TextView log = (TextView) findViewById(R.id.log);
		if (req_id == -1) {
			terminate("wrong password");
			return;
		}
		if (logged_in == false) {
			log.append("login successful\n");
			logged_in = true;
			return;
		}
		log.append(payload + (payload.endsWith("\n") ? "" : "\n"));
    }
    
    public void send(View v) {
    	EditText input = (EditText) findViewById(R.id.message);
    	String message = input.getText().toString();
    	input.setText("");
    	if (!message.equals("")) {
    		send_message(message);
    	}
    }
    
    class ChatReceiver extends Thread {
    	public ChatReceiver() {
    		start();
    	}
    	
    	@Override
    	public void run() {
    		while (true) {
    			try {
					int len = 0;
					for (int i=0; i<4; i++) {
						len += (((int)in.read())&0xff) << (i*8);
					}
					final byte[] str = new byte[len-4-4-2];
					int req_id = 0;
					for (int i=0; i<4; i++) {
						req_id += (((int)in.read())&0xff) << (i*8);
					}
					final int req_id_ = req_id;
					int type = 0;
					for (int i=0; i<4; i++) {
						type += (((int)in.read())&0xff) << (i*8);
					}
					final int type_ = type;
					int str_todo = str.length;
					while (str_todo != 0) {
						int res = in.read(str, str.length - str_todo, str_todo);
						if (res == -1) throw new IOException("unexpected eof");
						str_todo -= res;
					}
					if ((in.read() | in.read()) != 0) {
						throw new IOException("expected NULLs");
					}
					h.post(new Runnable() {
						public void run() {
							handle_packet(req_id_, type_, new String(str));
						}
					});
				} catch (final IOException e) {
					h.post(new Runnable() {
						public void run() {
							if (!dead) {
								terminate(e.getLocalizedMessage());
							}
						}
					});
					return;
				}
    		}
    	}
    }
}