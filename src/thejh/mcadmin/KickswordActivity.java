package thejh.mcadmin;

import java.util.LinkedList;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class KickswordActivity extends Activity {
	private ArrayAdapter<String> adapter;
	private static LinkedList<String> targets = new LinkedList<String>();
	
	private SensorManager sm;
	private SensorEventListener ac_l;
	private SensorEventListener cp_l;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kicksword);
        
        ListView mListView = (ListView) findViewById(R.id.player_list);
        adapter = new ArrayAdapter<String>(this, R.layout.simple_text, targets);
        mListView.setAdapter(adapter);
        mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
        	public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
        		adapter.remove(targets.get(position));
				return true;
			}
		});
        
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
    }
    
    private float current_angle = 0;
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	sm.registerListener(ac_l = new SensorEventListener() {
        	private double lastpeak = 0;
			public void onSensorChanged(SensorEvent event) {
				double accel_sum = Math.sqrt(event.values[0]*event.values[0]
						                    +event.values[1]*event.values[1]
						                    +event.values[2]*event.values[2]);
				double g_diff = Math.abs(SensorManager.GRAVITY_EARTH - accel_sum);
				double tdiff = event.timestamp - lastpeak;
				if (g_diff > 50 && tdiff > 5e9) {
					lastpeak = event.timestamp;
					String user = getNearestUser(current_angle);
					if (user == null) return;
					Intent i = new Intent();
					i.putExtra("nextstep", "kick_do");
					i.putExtra("player", user);
					setResult(42, i);
					finish();
				}
			}
			
			public void onAccuracyChanged(Sensor sensor, int accuracy) {}
		}, sm.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0), SensorManager.SENSOR_DELAY_GAME);
    	
    	sm.registerListener(cp_l = new SensorEventListener() {
			public void onAccuracyChanged(Sensor sensor, int accuracy) {}

			public void onSensorChanged(SensorEvent event) {
				current_angle = event.values[0];
				TextView t = (TextView)findViewById(R.id.current_angle);
				t.setText(Math.round(current_angle)+"°");
			}
    	}, sm.getSensorList(Sensor.TYPE_ORIENTATION).get(0), SensorManager.SENSOR_DELAY_GAME);
    }
    
    private String getNearestUser(float targetAngle) {
    	Log.d("MCsword", "target_angle="+targetAngle);
		String best_fit = null;
		float best_fit_angle_diff = 0;
		for (String entry: targets) {
			String[] parts = entry.split(" ");
			int angle = Integer.parseInt(parts[0], 10);
			String name = parts[1];
			float angle_diff = Math.abs(targetAngle - (float)angle);
			if (angle_diff > 180) angle_diff = 360 - angle_diff;
			Log.d("MCsword", "angle="+angle+" name="+name+" angle_diff="+angle_diff);
			if (best_fit == null || best_fit_angle_diff > angle_diff) {
				best_fit_angle_diff = angle_diff;
				best_fit = name;
			}
		}
		return best_fit;
	}

	@Override
    protected void onPause() {
    	super.onPause();
    	sm.unregisterListener(ac_l);
    	sm.unregisterListener(cp_l);
    }
    
    private String getETString(int id) {
    	EditText e = (EditText) findViewById(id);
    	String s = e.getText().toString();
    	e.setText("");
    	return s;
    }
    
    public void add_user(View view) {
    	adapter.add(getETString(R.id.angle)+" "+getETString(R.id.user));
    }
}
