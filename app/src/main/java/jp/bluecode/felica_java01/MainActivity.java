package jp.bluecode.felica_java01;

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Formatter;

public class MainActivity extends AppCompatActivity {

    //Widgetの宣言
    TextView txt_idm;
    TextView txt_pmm;
    TextView txt_waonno;
    TextView txt_fpno;
    TextView txt_msg;
    Button btn_start;
    Button btn_stop;
    Button btn_put;

    //nfc
    NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Widget初期化
        txt_idm = findViewById(R.id.txt_idm);
        txt_pmm = findViewById(R.id.txt_pmm);
        txt_waonno = findViewById(R.id.txt_waonno);
        txt_fpno = findViewById(R.id.txt_fpno);
        txt_msg = findViewById(R.id.txt_msg);
        btn_start = findViewById(R.id.btn_start);
        btn_stop = findViewById(R.id.btn_stop);
        btn_put = findViewById(R.id.btn_put);

        //nfc初期化
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        //初期設定
        btn_stop.setEnabled(false);
        btn_put.setEnabled(false);

        //イベント定義
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //トグル制御
                btn_start.setEnabled(false);
                btn_stop.setEnabled(true);

                //Reader Mode On
                nfcAdapter.enableReaderMode(MainActivity.this,new MyReaderCallback(),NfcAdapter.FLAG_READER_NFC_F,null);

                //表示制御
                txt_idm.setText("");
                txt_pmm.setText("");
                txt_waonno.setText("");
                txt_fpno.setText("");

            }
        });

        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //トグル設定
                btn_start.setEnabled(true);
                btn_stop.setEnabled(false);

                //Reader Mode Off
                nfcAdapter.disableReaderMode(MainActivity.this);

            }
        });

        btn_put.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

    }

    //callback inner class
    private class MyReaderCallback implements NfcAdapter.ReaderCallback{
        @Override
        public void onTagDiscovered(Tag tag){

            NfcF nfc = NfcF.get(tag);

            //getId()で取得できるIDmはSystem0のもののよう。複数のシステムコードがある場合は注意
            final byte[] _idm = tag.getId();
            Log.d("Hoge","_idm from tag.getId()=" + toHex(_idm));

            try{

                nfc.connect();

                //request用のbyte列
                //0x06:コマンド長, 0x00:polling, 0xFE00:system code（共通領域）, 0x00:リクエストコード, 0x00:time slot
                byte[] polling_request = {(byte)0x06,(byte)0x00,(byte)0xFE,(byte)0x00,(byte)0x00,(byte)0x00};
                //response用のbyte列
                byte[] polling_response = null;

                //コマンド送信・受信
                polling_response = nfc.transceive(polling_request);

                Log.d("Hoge","polling_response=" + toHex(polling_response));

                //idmの取り出し
                byte[] idm = Arrays.copyOfRange(polling_response,2,10);
                //pmmの取り出し
                byte[] pmm = Arrays.copyOfRange(polling_response,11,19);

                //byte列を文字列に変換
                final String idmString = bytesToHexString(idm);
                final String pmmString = bytesToHexString(pmm);

                //waonno処理

                //waonnno request
                //カスタム関数をつかってrequestコマンドを組み立て
                byte[] waonno_request = readWithoutEncryption(idm,2);
                //コマンド送信・受信
                byte[] wannno_response = nfc.transceive(waonno_request);

                //WAON番号部分を切り取り
                byte[] waonno = Arrays.copyOfRange(wannno_response,13,21);

                //文字列変換
                final String waonnoString = bytesToHexString(waonno);

                //FPNo処理

                byte[] fpno_request = readWithoutEncryption2(idm,1);
                byte[] fpno_response = nfc.transceive(fpno_request);
                byte[] fpno = Arrays.copyOfRange(fpno_response,13,17);

                //fpnoは10進数に変換する必要がある
                String fpnoHexString = bytesToHexString(fpno);
                Integer fpnoDexInt = Integer.parseInt(fpnoHexString,16);
                final String fpnoString = fpnoDexInt.toString();


                //親スレッドのUI更新
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        txt_idm.setText(idmString);
                        txt_pmm.setText(pmmString);
                        txt_waonno.setText(waonnoString);
                        txt_fpno.setText(fpnoString);

                        //msg
                        txt_msg.setText(bytesToHexString(_idm));
                    }
                });

                nfc.close();

            }catch(Exception e){
                Log.e("Hoge",e.getMessage());
            }
        }
    }

    //bytesを16進数型文字列に変換
    private String bytesToHexString(byte[] bytes){
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        Formatter formatter = new Formatter(sb);
        for(byte b: bytes){
            formatter.format("%02x",b);
        }
        formatter.close();
        return sb.toString().toUpperCase();
    }



    //非暗号領域読み取りコマンド（WAON番号領域特化）
    private byte[] readWithoutEncryption(byte[] idm, int blocksize) throws IOException {

        ByteArrayOutputStream bout = new ByteArrayOutputStream(100); //とりあえず

        //readWithoutEncryptionコマンド組み立て
        bout.write(0); //コマンド長（後で入れる）
        bout.write(0x06); //0x06はRead Without Encryptionを表す
        bout.write(idm); //8byte:idm
        bout.write(1); //サービス数
        bout.write(0x4f); //サービスコードリスト WAONカード番号は684F
        bout.write(0x68); //サービスコードリスト
        bout.write(blocksize); //ブロック数

        for(int i=0; i<blocksize; i++){
            bout.write(0x80); //ブロックリスト
            bout.write(i);
        }

        byte[] msg = bout.toByteArray();
        msg[0] = (byte)msg.length;

        return msg;
    }

    //非暗号領域読み取りコマンド（FPNo領域特化）
    private byte[] readWithoutEncryption2(byte[] idm, int blocksize) throws IOException {

        ByteArrayOutputStream bout = new ByteArrayOutputStream(100); //とりあえず

        //readWithoutEncryptionコマンド組み立て
        bout.write(0); //コマンド長（後で入れる）
        bout.write(0x06); //0x06はRead Without Encryptionを表す
        bout.write(idm); //8byte:idm
        bout.write(1); //サービス数
        bout.write(0x4b); //サービスコードリスト FPNo（カード識別情報）は394B
        bout.write(0x39); //サービスコードリスト
        bout.write(blocksize); //ブロック数

        for(int i=0; i<blocksize; i++){
            bout.write(0x80); //ブロックリスト
            bout.write(i);
        }

        byte[] msg = bout.toByteArray();
        msg[0] = (byte)msg.length;

        return msg;
    }

    private String toHex(byte[] id) {
        StringBuilder sbuf = new StringBuilder();
        for (int i = 0; i < id.length; i++) {
            String hex = "0" + Integer.toString((int) id[i] & 0x0ff, 16);
            if (hex.length() > 2)
                hex = hex.substring(1, 3);
            sbuf.append(" " + i + ":" + hex);
        }
        return sbuf.toString();
    }
}
