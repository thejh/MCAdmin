package thejh.mcadmin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
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
	private String pending_action;
	private NamesListener on_names;
	private LinkedList<String> angles = new LinkedList<String>();
	private SensorManager sm;
	private SensorEventListener cp_l;
	private float current_angle;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        h = new Handler(); // TODO is posting to this queue without synchronization really ok?
        Intent i = new Intent(this, LoginActivity.class);
        startActivityForResult(i, 1);
        
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        
        Button b = (Button) findViewById(R.id.drag_drop_tp);
        b.setOnTouchListener(new OnTouchListener() {
        	private String drag_subject = null;
        	
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					drag_subject = KickswordActivity.getNearestUser(angles, current_angle);
					Log.d("MCdrag", "drag start with angle="+current_angle+" user="+drag_subject);
					return true;
				} else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
					String target = KickswordActivity.getNearestUser(angles, current_angle);
					Log.d("MCdrag", "drop with angle="+current_angle+" user="+drag_subject);
					if (drag_subject.equals(target)) return false;
					send_message("tp "+drag_subject+" "+target);
					drag_subject = null;
					return true;
				}
				return false;
			}
		});
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	KickswordActivity.update_angles(angles, this);
    	
    	sm.registerListener(cp_l = new SensorEventListener() {
			public void onAccuracyChanged(Sensor sensor, int accuracy) {}

			public void onSensorChanged(SensorEvent event) {
				current_angle = event.values[0];
			}
    	}, sm.getSensorList(Sensor.TYPE_ORIENTATION).get(0), SensorManager.SENSOR_DELAY_GAME);
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	sm.unregisterListener(cp_l);
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
    		if (host==null || sport==null || pass==null) {
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
    	} else if (requestCode == 2 && resultCode == 42) {
    		String method_to_run = data.getStringExtra("nextstep");
    		Method m = null;
    		try {
				m = MCAdminActivity.class.getMethod(method_to_run, Intent.class);
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
				System.exit(1);
			}
    		try {
				m.invoke(this, data);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				System.exit(1);
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				System.exit(1);
			} catch (InvocationTargetException e) {
				e.printStackTrace();
				System.exit(1);
			}
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
		if (on_names != null && payload.startsWith("Connected players:")) {
			Log.d("MC", "got automatically requested names list");
			ArrayList<String> names = new ArrayList<String>();
			for (String name: payload.split(":")[1].split(",")) {
				name = name.trim();
				if (name.equals("")) continue;
				names.add(name);
			}
			on_names.run(names);
			on_names = null;
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
    
    private void select_player(final String nextstep, final String text) {
    	on_names = new NamesListener() {
			public void run(ArrayList<String> names) {
				Intent i = new Intent(self, SelectPlayerActivity.class);
				i.putExtra("names", names.toArray(new String[]{}));
				i.putExtra("text", text);
				i.putExtra("nextstep", nextstep);
				startActivityForResult(i, 2);
			}
		};
    	send_message("list");
    }
    
    private String teleport_user_1;
    public void teleport(View view) {
    	select_player("teleport_select_dst", "Please choose who should be teleported.");
    }
    public void teleport_select_dst(Intent i) {
    	teleport_user_1 = i.getStringExtra("player");
    	select_player("teleport_do", "Please choose to whom "+teleport_user_1+" should be teleported.");
    }
    public void teleport_do(Intent i) {
    	send_message("tp "+teleport_user_1+" "+i.getStringExtra("player"));
    }
    
    public void kick(View view) {
    	select_player("kick_do", "Please choose which player should be kicked.");
    }
    public void kick_do(Intent i) {
    	send_message("kick "+i.getStringExtra("player"));
    }
    public void ban(View view) {
    	select_player("ban_do", "Please choose which player should be banned.");
    }
    public void ban_do(Intent i) {
    	send_message("ban "+i.getStringExtra("player"));
    }
    
    public void survival(View v) {
    	select_player("survival_do", "Please choose which player should be set to gamemode 0 (survival).");
    }
    public void survival_do(Intent i) {
    	send_message("gamemode "+i.getStringExtra("player")+" 0");
    }
    public void creative(View v) {
    	select_player("creative_do", "Please choose which player should be set to gamemode 1 (creative).");
    }
    public void creative_do(Intent i) {
    	send_message("gamemode "+i.getStringExtra("player")+" 1");
    }
    
    public void op(View v) {
    	select_player("op_do", "Please choose which player should be granted operator access.");
    }
    public void op_do(Intent i) {
    	send_message("op "+i.getStringExtra("player"));
    }
    public void deop(View v) {
    	select_player("deop_do", "Please choose whose operator access should be revoked.");
    }
    public void deop_do(Intent i) {
    	send_message("deop "+i.getStringExtra("player"));
    }
    
    public void launch_kicksword(View view) {
    	Intent i = new Intent(this, KickswordActivity.class);
    	startActivityForResult(i, 2);
    }
    
    interface NamesListener {
    	public void run(ArrayList<String> names);
    }
}