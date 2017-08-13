package com.baidu.trackshow;

/**
 * Created by EisenloCarter on 2016/9/28.
 */

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.baidu.trackshow.Login;

import java.io.OutputStream;

public class UserInformation extends Activity {

    private Button back;
    private TextView number;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_info);

        Login login = new Login();
        number = (TextView) findViewById(R.id.number);
        number.setText(login.superNum);

        back = (Button) findViewById(R.id.back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

}