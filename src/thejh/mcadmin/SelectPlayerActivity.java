package thejh.mcadmin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class SelectPlayerActivity extends Activity {
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_player);
        final Intent i = getIntent();
        final String[] names = i.getStringArrayExtra("names");
        
        String text = i.getStringExtra("text");
        ((TextView)findViewById(R.id.player_list_instruction)).setText(text);
        
        ListView mListView = (ListView) findViewById(R.id.player_list);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.simple_text, names);
        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {
        	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        		Intent i_new = new Intent();
        		i_new.putExtra("player", names[position]);
        		i_new.putExtra("nextstep", i.getStringExtra("nextstep"));
				setResult(42, i_new);
				finish();
			}
		});
    }
}
