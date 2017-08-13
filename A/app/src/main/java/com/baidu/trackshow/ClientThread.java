package com.baidu.trackshow;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Created by ASUS on 2016/10/1.
 */


public class ClientThread implements Runnable {
    private Socket s;
    private Socket s1;
    String content = null;
    // 定义向UI线程发送消息的Handler对象
    Handler handler;
    // 定义接收UI线程的Handler对象
    Handler revHandler;
    // 该线程处理Socket所对用的输入输出流
    BufferedReader br = null;
    //OutputStream os = null;
    DataOutputStream dos = null;
    FileInputStream fis = null;
    OutputStream os= null;

    public ClientThread(Handler handler) {
        this.handler = handler;
    }


    @Override
    public void run() {
        s = new Socket();
        try {
            s.connect(new InetSocketAddress("192.168.43.86", 8888), 5000);
//             启动一条子线程来读取服务器相应的数据
            new Thread() {
                @Override
                public void run() {
                    try {

                        br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                        content=br.readLine();

                    } catch (IOException e) {

                        e.printStackTrace();
                    }finally {
                        Message msg = new Message();
                        msg.what = 0x123;
                        msg.obj = content;
                        handler.sendMessage(msg);
                        //dos和fis也只能在这里关闭
                        if (dos != null)
                            try {
                                dos.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        if (fis != null)
                            try {
                                fis.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        if (s != null) {
                            try {
                                s.close();//这里才能关闭比socket，否则无法先向服务器传送数据后接受服务器的返回值
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }.start();
            // 为当前线程初始化Looper
            Looper.prepare();

            revHandler = new Handler() {

                @Override
                public void handleMessage(Message msg) {
                    // 接收到UI线程的中用户输入的数据
                    if (msg.what == 0x345) {
                        int length = 0;

                        byte[] sendBytes = null;

                        //将手机获取的总信息进行分割
                        String totalInfo=msg.obj.toString();
                        String stuNum=totalInfo.substring(0,totalInfo.indexOf('#'));//获取总信息中学号，以‘#’结尾
                        String filePath = totalInfo.substring(totalInfo.indexOf('#') + 1, totalInfo.length());
                        File file= new File(filePath);
                        try {
                            os = s.getOutputStream();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        PrintWriter pw=new PrintWriter(os);
                        pw.write(stuNum);
                        pw.flush();
                        // 将用户在文本框输入的内容写入网络
                        try {
                            dos = new DataOutputStream(s.getOutputStream());
                            // File file = new File(msg.obj.toString());
                            fis = new FileInputStream(file);
                            sendBytes = new byte[1024 * 1024];
                            while ((length = fis.read(sendBytes, 0, sendBytes.length)) > 0) {
                                dos.write(sendBytes, 0, length);
                                dos.flush();
                            }
                        } catch (FileNotFoundException e1) {
                            e1.printStackTrace();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                }

            };
            // 启动Looper
            Looper.loop();
        } catch (SocketTimeoutException e) {
            Message msg = new Message();
            msg.what = 0x123;
            msg.obj = "网络连接超时！";
            handler.sendMessage(msg);
        } catch (IOException io) {
            io.printStackTrace();
        }

    }
}