package com.baidu.trackshow;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Login extends AppCompatActivity {
    private static final int TAKE_PHOTO = 1;
    private static final int CROP_PHOTO = 2;
    private static final int REQUEST_CODE = 0;

    private Button takePic;
    private Button SendPic;
    private Button enter;
    private Button enterFake;

    private ImageView mImageView;

    public EditText mEditText;

    private TextView show;

    private String mFilePath;
    private String stuNum="";
    static public String superNum ;;


    boolean flag = false;

    private Uri picUri;

    com.baidu.trackshow.ClientThread clientThread;

    Handler handler;

    private PermissionsChecker mPermissionsChecker;

    static final String[] PERMISSIONS = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        takePic = (Button) findViewById(R.id.take_pic);
        SendPic = (Button) findViewById(R.id.send_pic);
        enter = (Button) findViewById(R.id.enter);
        enterFake = (Button) findViewById(R.id.enterFake);

        mEditText = (EditText) findViewById(R.id.stu_num);

        show = (TextView) findViewById(R.id.text_view);

        mImageView = (ImageView) findViewById(R.id.image_view);

        mFilePath = Environment.getExternalStorageDirectory().getPath();
        mFilePath += "/" + "temp.png";

        mPermissionsChecker = new PermissionsChecker(this);

        handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                // 如果消息来自子线程
                if (msg.what == 0x123) {
                    // 将读取的内容追加显示在文本框中
                    show.append(msg.obj.toString());
                    if (msg.obj.toString().equals("RESULT_OK")) {
                        enterFake.setVisibility(View.GONE);
                        enter.setVisibility(View.VISIBLE);

                    }

                }
            }
        };

        takePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                picUri = Uri.fromFile(new File(mFilePath));
                intent.putExtra(MediaStore.EXTRA_OUTPUT, picUri);
                startActivityForResult(intent, TAKE_PHOTO);
            }
        });

        clientThread = new com.baidu.trackshow.ClientThread(handler);
        // 客户端启动ClientThread线程创建网络连接、读取来自服务器的数据
        new Thread(clientThread).start();

        enter.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){

                Intent intent = new Intent();
                // 设置要跳转的页面
                intent.setClass(Login.this, MainActivity.class);
                // 开始Activity
                startActivity(intent);
            }
        });

        SendPic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEditText.getText().toString().equals(stuNum)) {
                    Toast.makeText(Login.this, "请先输入学号!", Toast.LENGTH_SHORT).show();
                } else {
                    String stu = mEditText.getText().toString();
                    superNum = mEditText.getText().toString();
                    if (flag) {
                        try {
                            Message msg = new Message();
                            msg.what = 0x345;
                            msg.obj = stu+"#"+mFilePath;
                            clientThread.revHandler.sendMessage(msg);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(Login.this, "请先完成拍照!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPermissionsChecker.lacksPermissions(PERMISSIONS)) {
            startPermissionsActivity();
        }
    }

    private void startPermissionsActivity() {
        PermissionsActivity.startActivityForResult(this, REQUEST_CODE, PERMISSIONS);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case TAKE_PHOTO:
                if (resultCode == RESULT_OK) {

                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(mFilePath);
                        Bitmap bitmap = BitmapFactory.decodeStream(fis);
                        mImageView.setImageBitmap(bitmap);
                        flag = true;
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            fis.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    //if (requestCode == REQUEST_CODE && resultCode == PermissionsActivity.PERMISSIONS_DENIED) {
                    //  finish();
                    //}
                    Intent intent = new Intent("com.android.camera.action.CROP");
                    intent.setDataAndType(picUri, "image/**");
                    intent.putExtra("scale", true);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, picUri);
                    startActivityForResult(intent,CROP_PHOTO);
                    break;
                }
            case CROP_PHOTO:
                if (resultCode == RESULT_OK) {
                    try {
                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(picUri));
                        mImageView.setImageBitmap(bitmap);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                break;
        }

    }
}
