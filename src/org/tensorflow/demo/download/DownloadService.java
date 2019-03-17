package org.tensorflow.demo.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.IBinder;
import android.os.Binder;
import android.widget.Toast;
import android.support.v4.app.NotificationCompat;

import org.tensorflow.demo.CameraActivity;
import org.tensorflow.demo.R;

import java.io.File;

public class DownloadService extends Service {

    private DownloadTask downloadTask;

    private String downloadUrl;

    private DownloadListener listener = new DownloadListener() {
        @Override
        public void onProgress(int progress) {
            getNotifictionManager().notify(1, getNotifiction("下载中...",progress));
            //通知栏显示
        }

        @Override
        public void onSuccess() {
            downloadTask = null;
            stopForeground(true);
            getNotifictionManager().notify(1, getNotifiction("下载成功",-1));//通知栏显示
            Toast.makeText(DownloadService.this, "下载成功", Toast.LENGTH_SHORT).show();//弹出下载成功消息
        }

        @Override
        public void onFailed() {
            downloadTask = null;
            stopForeground(true);
            getNotifictionManager().notify(1, getNotifiction("下载失败",-1));
            Toast.makeText(DownloadService.this, "下载失败", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPaused() {
            downloadTask = null;
            stopForeground(true);
            Toast.makeText(DownloadService.this, "暂停", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCanceled() {
            downloadTask = null;
            stopForeground(true);
            Toast.makeText(DownloadService.this, "取消", Toast.LENGTH_SHORT).show();

        }
    };

    private DownloadBinder mBinder = new DownloadBinder();

    @Override
    public IBinder onBind(Intent intent){
        return mBinder;
    }

    class DownloadBinder extends Binder{

        public void startDownload(String url){
            if (downloadTask == null){//开始下载对downloadTask进行初始化
                downloadUrl = url;
                downloadTask = new DownloadTask(listener);
                downloadTask.execute(downloadUrl);
                startForeground(1, getNotifiction("下载中...",0));//通知栏显示
                Toast.makeText(DownloadService.this,"下载中...",Toast.LENGTH_SHORT).show();
                //弹出下载中消息
            }
        }

        public void pauseDownload(){
            if (downloadTask != null){
                downloadTask.pauseDownload();
            }
        }

        public void cancelDownload(){
            if (downloadTask != null){
                downloadTask.cancelDownload();
            }else{
                if (downloadUrl != null){
                    String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
                    String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                    File file = new File(directory + fileName);
                    if (file.exists()){//删除模型文件
                        file.delete();
                    }
                    getNotifictionManager().cancel(1);
                    stopForeground(true);//撤销通知栏显示
                    Toast.makeText(DownloadService.this,"取消下载",Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private NotificationManager getNotifictionManager(){
        return (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    private Notification getNotifiction(String title, int progress){
        Intent intent = new Intent(this, CameraActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,intent,0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.ic_launcher));
        builder.setContentIntent(pi);
        builder.setContentTitle(title);
        if(progress > 0){
            builder.setContentText(progress + "%");
            builder.setProgress(100, progress, false);
        }
        return builder.build();
    }

}
