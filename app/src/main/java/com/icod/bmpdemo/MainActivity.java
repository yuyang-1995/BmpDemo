package com.icod.bmpdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {


    private Button btn_on, btn_off, btn_print;
    private ImageView iv;

    private UsbManager mUsbManager;
    private UsbDevice mDevice;

    private UsbDeviceConnection mUsbDeviceConnection;

    private PendingIntent mPermissionIntent;
    private TextView textView;

    private UsbEndpoint mEndpointIn;
    private UsbEndpoint mEndpointOut;

    String ivname = "text.bmp";

    private int hasPermission = -2;

    Bitmap ivbitmap;

    private static final String ACTION_USB_PERMISSION = "com.szsicod.print.USB_PERMISSION";

    private UsbBroadCastReceiver mUsbBroadCastReceiver;

    private boolean unRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        btn_on = findViewById(R.id.btn_open);

        btn_off = findViewById(R.id.btn_close);

        textView = findViewById(R.id.tv);

        textView.setText(" ");




        btn_off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                closeDevice();

            }
        });

        iv = findViewById(R.id.iv);

        //开启线程连接usb
        btn_on.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //如果已近连接则提示
                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        if (openDevice() == 0){

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    iv.setImageBitmap(null);

                                    textView.setText("状态：连接成功。。。");
                                }
                            });

                        }else {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    textView.setText("状态：连接失败请重连。。。");
                                }
                            });

                        }
//                      openDevice();  //连接设备

                      while (true){

                          if (isOkToScan()){

                              runOnUiThread(new Runnable() {
                                  @Override
                                  public void run() {
                                      textView.setText("状态:等待扫描中。。。");
                                  }
                              });


                              Log.e("isOk", "True");
                              if (mUsbDeviceConnection != null) {
                                  //mUsbDeviceConnection
                                  //获取数据
                                  getDataFromUsb();

                                  //
                                  showBmpToIv();

//                                  break;
                              }else {

                                System.out.println("mUsbDeviceConnection == null");
                              }

                          }else {
                              Log.e("isOk", "False");
                          }
                      }

                    }
                }).start();
            }
        });
    }


     //是否可以接受图片数据
     boolean isOkToScan(){
        //第一步， 发送 查询扫描命令，打印机会返回信息解析信息 看有无数据
         //
         byte b1 = (byte) 0xAA;
         byte b2 = 0x55;

         byte b3 = 0x02;  //扫描查询， 看有无数据
         byte b3s = 0x00;

         byte b4 = 0x0e;  //lenl
         byte b5 = 0x00;   //lenh

         //n1h n1l n2h n2l n3h n3l
         byte b6 = 0x00;
         byte b7 = 0x00;
         byte b8 = 0x00;
         byte b9 = 0x00;
         byte b10 = 0x00;
         byte b11 = 0x00;

         //求和
         int b1i = 170; // 十六进制AA 的的十进制
         int b2i = 85; //十六进制55 的十进制
         int b5i = 14; //十六进制 e的十进制
         int b3i = 2; //扫描指令

         int sum = b1i + b2i + b5i +b3i;
         //求和
         byte[] bytes1 = varIntToByteArray(sum);

         byte b12 =(byte) bytes1[1];
         byte b13 = (byte) bytes1[0];

         //发送命令
         byte[] bytes = new byte[]{b1, b2, b3,b3s, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13};

         //接受容器
         byte[] bytes2 = new byte[14];

         StringBuilder stringBuilder = new StringBuilder();
         for (int i = 0; i < bytes.length ; i++){
             stringBuilder.append(bytes[i] + "  ");
         }

         Log.e("ZL", stringBuilder.toString());
         //发送扫描指令 查询扫描，看有没有数据
          writeIO(bytes, 0, bytes.length, 1000);
         //接受数据
         while (true){
               //每次读14 个字节
             int ret = mUsbDeviceConnection.bulkTransfer(mEndpointIn, bytes2, 14,1000);
             if (ret >= 0) {
                 System.out.println("ret2: " + ret);
                 break;
             }else {
                 Log.d("Fail", ret + "");
             }
      }
         if (bytes2[11] != 0 || bytes2[10] != 0){
             return true;
         }else {
             System.out.println( "十 " + bytes2[10] + " 十一 "  +bytes2[11]);
             return false;
         }
     }

    /**
     * 写入指令
     *
     * @param writeBuffer 指令字节数组
     * @param offsetSize  偏移量，一般为0
     * @param writeSize   数组大小
     * @param waitTime    等待时间
     * @return
     */
    public synchronized int writeIO(byte[] writeBuffer, int offsetSize,
                                    int writeSize, int waitTime) {

            StringBuilder builder = new StringBuilder(writeBuffer.length);
            for (int i = 0; i < writeSize; i++) {
                String s = Integer.toHexString(writeBuffer[i]);
                if (s.length() > 2) {
                    s = s.substring(s.length() - 2);
                }
                if (s.length() < 2) {
                    s = "0" + s;
                }
                builder.append(s);
                builder.append(" ");
            }

        int ret = writeBuffer(writeBuffer, offsetSize, writeSize,
                waitTime);

        if (ret < 0) {

            return -1;

        }

        Log.i("数据传输结束","TAG");
        return ret;
    }

    //
    public int writeBuffer(byte[] writeBuffer, int offsetSize, int writeSize,
                           int waitTime) {
        int MAXSZIE = 512;

        if (null == mUsbDeviceConnection || null == mEndpointOut) {
            Log.e("TAG", "mUsbDeviceConnection为空不能输入");
            return -1;
        }

        if (offsetSize > writeSize) {
            return -2;
        }

        int writed = 0;
        int writeTrueSize = MAXSZIE;
        int copySize = 0;
        int allTransferSize = 0;
        byte data[] = null;

        while (writeSize - writed - offsetSize > 0) {
            int num = writeSize - writed - offsetSize;
            if (num < MAXSZIE) {
                writeTrueSize = num;
                data = new byte[writeTrueSize];
            } else {
                writeTrueSize = MAXSZIE;
                if (data == null) {
                    data = new byte[writeTrueSize];
                }
            }

            System.arraycopy(writeBuffer, writed + offsetSize, data, 0, writeTrueSize);
            copySize += writeTrueSize;
            writed = copySize;

            int write = mUsbDeviceConnection.bulkTransfer(mEndpointOut, data, data.length, waitTime);

            if (write == -1) {
                Log.e("传输失败","TAG");
                return -1;
            }
            allTransferSize += write;
        }

        if (allTransferSize == writeSize - offsetSize) {
            Log.i("已经全部传输完成 " , allTransferSize + "");
        } else {
            Log.e("usb 数据传输缺失 已经传输", allTransferSize + " 传输总数" + (writeSize - offsetSize));
        }

        return allTransferSize;
    }

    public static byte[] varIntToByteArray(long value) {
        Long l = new Long(value);
        byte[] valueBytes = null;
        if (l == l.byteValue()) {
            valueBytes = toBytes(value, 1);
        } else if (l == l.shortValue()) {
            valueBytes = toBytes(value, 2);
        } else if (l == l.intValue()) {
            valueBytes = toBytes(value, 4);
        } else if (l == l.longValue()) {
            valueBytes = toBytes(value, 8);
        }
        return valueBytes;
    }

    private static byte[] toBytes(long value, int len) {
        byte[] valueBytes = new byte[len];
        for (int i = 0;i < len;i++) {
            valueBytes[i] = (byte) (value >>> 8 * (len - i - 1));
        }
        return valueBytes;
    }

    //展示图片
    void showBmpToIv(){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText("展示图片。。。");
            }
        });
        // 1. 判断sd卡状态, 如果是挂载的状态才继续, 否则提示
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {

        String path =  Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +ivname;

        try {
             ivbitmap = BitmapFactory.decodeStream(new FileInputStream(path));

             if (ivbitmap != null){

                 Message message = handler.obtainMessage(12, ivbitmap);
                 handler.sendEmptyMessage(12);

             }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        }else {
            Toast.makeText(MainActivity.this, "无 sd 卡", Toast.LENGTH_SHORT).show();
        }
    }


    //将扫描数据转bmp 文件
    void saveToBmp(int w, int h, byte[] bytes,  int lenght) throws Exception {
        //
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText("扫描数据转BMP文件。。。");
            }
        });

        byte[] bmpBytes = new byte[lenght + 54];

        byte[] fileHead = new byte[14];

        fileHead[0] = 'B';
        fileHead[1] = 'M';

        byte[] b4 = intTobyte(lenght+54, 4);

        //赋值文件长度
        for (int i = 0; i < 4; i++) {
            fileHead[2 + i] = b4[i];
        }

        fileHead[10] = 0x36;

        byte[] headInfo = new byte[40];

        headInfo[0] = 0x28;

        b4 = intTobyte(w, 4);

        byte[] b5 = intTobyte(h, 4);

        for (int i = 0; i < 4; i++) {
            headInfo[4 + i] = b4[i];
            headInfo[8 + i] = b5[i];
        }

        // 01 00 18 00
        headInfo[12] = 0x01;
        headInfo[14] = 0x18;

        byte[] b6 = intTobyte(lenght, 4);

        for (int i = 0; i < 4; i++) {
            headInfo[20+i] = b6[i];
        }

        //赋头
        System.arraycopy(fileHead, 0, bmpBytes, 0, fileHead.length);

        //
        System.arraycopy(headInfo, 0, bmpBytes, fileHead.length, headInfo.length);

        //
        System.arraycopy(bytes, 0, bmpBytes, 54, lenght);

        //保存图片
//        ivname = "test" + ++ivnum + ".bmp";

        //存入sd 卡
        saveToSDCard(ivname, bmpBytes);

        Log.e("save", "Save");
    }


    //存入sd
    public void saveToSDCard(String fileName, byte[] bytes) throws Exception{

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText("图片存入SD卡。。。");
            }
        });

        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            //SD卡已装入

            //第一个参数方法为获取SDCard目录
            File file = new File(Environment.getExternalStorageDirectory(),fileName);

            FileOutputStream outputStream = new FileOutputStream(file);

            outputStream.write(bytes);

            outputStream.close();
        }
    }


    //int 转byte
    public  byte[] intTobyte(int a,int len){

        byte[] t=new byte[len];


        t[0]=(byte) ((a&0xff));

        if(len>1)
            t[1]=(byte)((a&0xff00)>>8);
        if(len>2)
            t[2]=(byte)((a&0xff0000)>>16);
        if(len>3)
            t[3]=(byte)((a&0xff000000)>>24);

        return t;
    }


    //获取扫描数据
    void getDataFromUsb(){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                textView.setText("状态:获取扫描数据。。。");
            }
        });
        int widthPX = 1080;

        int heightPX = 40000;

        //图片大小
        int sizePx = widthPX * heightPX * 3;

        //图片容器
        byte[] zxSizeBytes = new byte[sizePx];

        System.out.println("zxSizeBytes:" + zxSizeBytes.length +"");

        int length = 0;

        //第二步， 发送03 查询扫描命令，打印机会返回信息解析信息 看有无数据
        byte b1 = (byte) 170;
        byte b2 = 0x55;

        byte b3 = 0x03;  //启动扫描向上发送数据
        byte b3s = 0x00;

        byte b4 = 0x00;  //lenh
        byte b5 = 0x0e;   //lenl

        //n1h n1l n2h n2l n3h n3l
        byte b6 = 0x00;
        byte b7 = 0x00;
        byte b8 = 0x00;
        byte b9 = 0x00;
        byte b10 = 0x00;
        byte b11 = 0x00;


        int b1i = 170; // 十六进制AA 的的十进制
        int b2i = 85; //十六进制55 的十进制
        int b5i = 14; //十六进制 e的十进制
        int b3i = 3; //扫描指令

        int sum = b1i + b2i + b5i +b3i;

        //求和
        byte[] bytes1 = varIntToByteArray(sum);

        byte b12 =(byte) bytes1[1];

        byte b13 = (byte) bytes1[0];

        //发送03 命令
        byte[] bytes = new byte[]{b1, b2, b3,b3s, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13};

        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < bytes.length ; i++){

            stringBuilder.append(bytes[i] + "  ");

        }

        Log.e("ZE", stringBuilder.toString());

        //发送03指令启动扫描向上发送数据
        writeIO(bytes, 0, bytes.length, 1000);

        //接受容器
        byte[] bytes2 = new byte[512];

        //一步一步接受图片数据
        while (true){
            //每次读 个字节, 每一个头
            //此处mUsbDeviceConnection.bulkTransfer 替换 mPrinter.readIO 方法
            int ret = mUsbDeviceConnection.bulkTransfer(mEndpointIn, bytes2, 512,1000);

            //返回有数据
            if (ret >= 0) {

                System.out.println("ret3: " + ret);

                //获取图片数据大小
                byte hightSize = bytes2[7];
                byte lowSize = bytes2[6];

                System.out.println("he:" + hightSize + "  low:" +lowSize);
                int hi = hightSize & 0x000000ff;

                int ls = lowSize & 0x000000ff;
                int usDataLen = (hi << 8) | ls;

                System.out.println("usDataLen" + usDataLen);

                //读取每个包的 数据， 直至没有下一个头
                //usDataLen = usDataLen & 0x0000ffff;

                //将每次读到的 图片数据封装到 图片大数组
                System.arraycopy(bytes2,14,zxSizeBytes, length , 498);

                length += 498;

                usDataLen-= 498;

                //单个图片数据包容器
//                byte[] packagebytes = new byte[usDataLen];

                System.out.println(usDataLen + "");

                int te = 0;

                for (int i = 0; i < usDataLen / 512; ) {

                    byte[] bytes3 = new byte[512];

                    int retw = mUsbDeviceConnection.bulkTransfer(mEndpointIn, bytes3, 512, 1000);

                    if (retw >= 0){
                        i++;
                        System.out.println("retw = " + retw);

                        //将每次读到的 图片数据封装到 图片大数组
                        System.arraycopy(bytes3,0,zxSizeBytes, length , retw);

                        length += retw;

                        te += retw;
                    }
                }

                int h = usDataLen-te;

                byte[] bytes4 = new byte[h];

                int retw = mUsbDeviceConnection.bulkTransfer(mEndpointIn, bytes4, h, 1000);

                if (retw >= 0){

                    System.out.println("retw = " + retw);

                    //将每次读到的 图片数据封装到 图片大数组
                    System.arraycopy(bytes4,0,zxSizeBytes, length , retw);

                    length += retw;
                }


                //判断该组数据后还有无数据
                if (bytes2[8] != 00 || bytes2[9] != 00){

                }else {

                    System.out.println("8: " + bytes2[8] + "9 :"  + bytes2[9] + "");
                    break;
                }

            }else {

                System.out.println("rete: " + ret);
            }
        }

        heightPX = length/3/ widthPX;

        System.out.println("heightPx: " + heightPX);

        //读完数据后， 将图片全部数据 转bmp 文件
        try {

            saveToBmp(widthPX,heightPX,zxSizeBytes,widthPX*heightPX*3);

        }catch (Exception e){

            e.printStackTrace();
        }
    }


    // 返回0 表示连接成功
      private int openDevice(){

        //判断是否已近连接
        if (isOpen()){

            handler.sendEmptyMessage(0);

            return 0;
        }

       //注册监听
        if (null == mUsbBroadCastReceiver){
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            mUsbBroadCastReceiver=new UsbBroadCastReceiver();
            registerReceiver(mUsbBroadCastReceiver,intentFilter);

        }

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        //申请
        mPermissionIntent = PendingIntent.getBroadcast(MainActivity.this, 0,
                  new Intent(ACTION_USB_PERMISSION), 0);

          HashMap<String, UsbDevice> deviceList = this.mUsbManager
                  .getDeviceList();

          //循环以查找的usb 接口与研科的 usb 接口匹配
          for (UsbDevice usbDevice : deviceList.values()) {

              this.mDevice = (usbDevice);
              Log.i(" ",this.mDevice.getVendorId() + " PID="
                      + this.mDevice.getProductId());

              Log.i(" " ,mDevice.getDeviceName());

              if (checkID(this.mDevice.getVendorId(), this.mDevice.getProductId())) {
                  Log.i(" "," 找到 匹配USB VID=" + this.mDevice.getVendorId() + " PID="
                          + this.mDevice.getProductId());
                  break;
              }

              this.mDevice = null;
          }

          if (this.mDevice == null) {

              Log.e(""," 找不到 匹配USB VID 和PID");

              return -1;

          }

          IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
          filter.setPriority(Integer.MAX_VALUE);
          MainActivity.this.registerReceiver(this.mUsbBroadCastReceiver, filter);

          //如果没有权限则申请
          if (!mUsbManager.hasPermission(this.mDevice)) {

              hasPermission = -2;

              this.mUsbManager.requestPermission(this.mDevice,
                      this.mPermissionIntent);

              int num = 0;

              while (!this.mUsbManager.hasPermission(this.mDevice)) {
                  try {
                      if (11 == num) {
                          Log.e("", "权限获取超时");
                          return -3;
                      }

                      num++;

                      if (num % 2 == 0) {
                          unregisterReceiver(mUsbBroadCastReceiver);
                          unRegister = true;
                          Thread.sleep(1000L);

                          if (this.mUsbManager.hasPermission(this.mDevice)) {
                              hasPermission = 0;
                              break;
                          }

                          registerReceiver(mUsbBroadCastReceiver, filter);

                          this.mUsbManager.requestPermission(this.mDevice,
                                  this.mPermissionIntent);

                          unRegister = false;
                      }

                      if (hasPermission == -1) {
                          Log.e("","用户不同意授权");
                          break;
                      }

                      Log.i("","usb 获取权限中....");
                  } catch (InterruptedException e) {
                      // TODO Auto-generated catch block
                      e.printStackTrace();
                  }
              }
          }

          if (!unRegister) {

              unregisterReceiver(mUsbBroadCastReceiver);
          }

          unRegister = true;
          if (hasPermission == -1) {
              //在尝试 检测权限
              if (!this.mUsbManager.hasPermission(this.mDevice)) {
                  Log.e("", "尝试检测失败");
                  return -1;
              } else {
                  Log.e("","尝试检测成功");
                  hasPermission = 0;
              }

          }

          mUsbDeviceConnection = mUsbManager.openDevice(mDevice);

          if (mUsbDeviceConnection == null) {
              Log.e("","mUsbDeviceConnection 为空 ");
              mDevice = null;
              return -1;
          }

          if (!InitIOConfig()) {
              closeDevice();
              return -1;
          }

        return 0;

      }


    //初始化注册
    private Boolean InitIOConfig() {
        this.mEndpointIn = null;
        this.mEndpointOut = null;

        if ((this.mDevice == null) || (this.mUsbDeviceConnection == null)) {
            Log.e("", "mUsbDeviceConnection 为空 ");
            return false;
        }

        int interfaceCount = 1;

        UsbInterface intf;
        UsbEndpoint endpoint;

        if (this.mDevice.getInterfaceCount() == 0) {
            Log.e("","InterfaceCount 为零个");
            return false;
        }

        //
        for (int n = 0; n < interfaceCount; n++) {

            intf = this.mDevice.getInterface(n);

            mUsbDeviceConnection.claimInterface(intf, true);

            int endpointCount = intf.getEndpointCount();

            if (endpointCount == 0) {
                return false;
            }

            for (int m = 0; m < endpointCount; m++) {

                endpoint = intf.getEndpoint(m);

                if (endpoint.getType() == 2) {
                    if (endpoint.getDirection() == 0) {
                      this.mEndpointOut = endpoint;
                    } else if (endpoint.getDirection() == 128) {

                        this.mEndpointIn = endpoint;
                    }
                }
            }
        }

        if ((this.mEndpointOut == null) || (this.mEndpointIn == null)) {
            Log.e("", " usb 找不到输入输出 ");
            return false;
        }

        return true;
    }

      //断开usb连接
    public int closeDevice() {

        this.mUsbDeviceConnection.close();
        this.mUsbDeviceConnection = null;
        if (!unRegister) {
          unregisterReceiver(mUsbBroadCastReceiver);
            unRegister = true;
        }
        Toast.makeText(this, "断开连接", Toast.LENGTH_SHORT).show();

        Log.i("","usb 设备关闭 ");

        return 0;
    }



    private class UsbCheckSRT {
        private int PID;
        private int VID;

        private UsbCheckSRT(int vid, int pid) {
            this.PID = pid;
            this.VID = vid;
        }

        private boolean check(int vid, int pid) {
            return (this.PID == pid) && (this.VID == vid);
        }
    }

    private UsbCheckSRT[] mUsbCheckSRT = {
            new UsbCheckSRT(1155, 30016),
            new UsbCheckSRT(1155, 22339),
            new UsbCheckSRT(1157, 30017),
            new UsbCheckSRT(6790, 30084),
            new UsbCheckSRT(3544, 5120),
            new UsbCheckSRT(483, 7540),
            new UsbCheckSRT(8401, 28680),
            new UsbCheckSRT(1659, 8965),
            new UsbCheckSRT(10473, 649),
            new UsbCheckSRT(1046, 20497),
            new UsbCheckSRT(7344, 3),
            new UsbCheckSRT(1155, 41064),
            new UsbCheckSRT(1659, 8963),
            new UsbCheckSRT(1027, 24577),
            new UsbCheckSRT(3034, 46880),
            new UsbCheckSRT(1027, 24577),
            new UsbCheckSRT(9390, 6162),
            new UsbCheckSRT(6790, 29987),
            new UsbCheckSRT(10400, 4485),
            new UsbCheckSRT(1155, 22336)

    };

      //检查是否属于研科usb 接口
    private boolean checkID(int pid, int vid) {

        for (UsbCheckSRT aMUsbCheckSRT : this.mUsbCheckSRT) {
            if (aMUsbCheckSRT.check(pid, vid)) {
                return true;
            }
        }
        return false;
    }

      private boolean isOpen(){

        return this.mUsbDeviceConnection != null;

      }


    //usb 监听 由于权限问题而且sdk内部有超时时间 ,适用情况应该是系统默认usb权限开放或者root 板则使用UsbNativeApi
    private class UsbBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            System.out.println("action" + action);
            //拔USB
            if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                UsbDevice usbDevice=  intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice!=null){

                    Toast.makeText(MainActivity.this,"USB已断开！",Toast.LENGTH_SHORT).show();

                }
                //插USB
            }else if(action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)){

                Toast.makeText(MainActivity.this,"USB已连接！",Toast.LENGTH_SHORT).show();

            }
        }
    }

    private static class NoLeakHandler extends Handler {
        WeakReference<MainActivity> wf = null;

        private NoLeakHandler(MainActivity activity) {
            wf = new WeakReference<MainActivity>(activity);
        }
    }

    @SuppressLint("HandlerLeak")
    Handler handler = new NoLeakHandler(MainActivity.this) {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {


                //-1 操作失败
                case -1:
                    Toast.makeText(MainActivity.this, "未连接usb",
                            Toast.LENGTH_SHORT).show();
                    break;

                //0 操作成功
                case 0:

                    Toast.makeText(MainActivity.this,"已连接usb",
                            Toast.LENGTH_SHORT).show();
                    break;


                case 12:

                    iv.setImageBitmap(ivbitmap);

                    break;

                default:break;

            }

        }

    };
}
