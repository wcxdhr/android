package org.tensorflow.demo.download;

import android.os.AsyncTask;
import android.os.Environment;
import android.renderscript.ScriptGroup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadTask extends AsyncTask<String, Integer, Integer> {

    public static final int TYPE_SUCCESS = 0;//下载成功状态
    public static final int TYPE_FAILED = 1;//下载失败状态
    public static final int TYPE_PAUSED = 2;//下载暂停状态
    public static final int TYPE_CANCELED = 3;//取消下载状态

    private DownloadListener listener;

    private boolean isCanceled = false;

    private boolean isPaused = false;

    private int lastProgress;

    public DownloadTask(DownloadListener listener){
        this.listener = listener;
    }

    @Override
    protected Integer doInBackground(String... params){
        InputStream is = null;//输入流
        RandomAccessFile savedFile = null;
        File file = null;
        try{
            long downloadedLength = 0;
            String downloadUrl = params[0];
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));//获取文件名
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            //获得系统下载路径
            file = new File(directory+fileName);//以下根据文件长度对下载状态进行判断
            if (file.exists()){
                downloadedLength = file.length();
            }
            long contentLength = getContentLength(downloadUrl);
            if (contentLength == 0){
                return TYPE_FAILED;
            }
            else if (contentLength == downloadedLength){
                return  TYPE_SUCCESS;
            }
            OkHttpClient client = new OkHttpClient();//使用OkHttp完成网络请求和应答
            Request request = new Request.Builder()
                    .addHeader("RANGE","bytes="+downloadedLength+"-")
                    .url(downloadUrl)
                    .build();
            Response response = client.newCall(request).execute();
            if (response != null){
                is = response.body().byteStream();
                savedFile = new RandomAccessFile(file, "rw");
                savedFile.seek(downloadedLength);
                byte[] b = new byte[1024];
                int total = 0;
                int len;
                while((len = is.read(b)) != -1){
                    if (isCanceled) {
                        return TYPE_CANCELED;
                    }
                    else if(isPaused){
                        return TYPE_PAUSED;
                    }
                    else{
                        total += len;
                        savedFile.write(b, 0, len);
                        int progress = (int) ((total + downloadedLength) * 100 / contentLength);
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (is != null){
                    is.close();
                }
                if (savedFile != null){
                    savedFile.close();
                }
                if (isCanceled && file != null){
                    file.delete();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return TYPE_FAILED;
    }

    @Override
    protected void onProgressUpdate(Integer... values){
        int progress = values[0];
        if (progress > lastProgress){//对进度进行实时更新
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    @Override
    protected void onPostExecute(Integer status){
        switch(status){
            case TYPE_SUCCESS:
                listener.onSuccess();//下载成功
                break;
            case TYPE_CANCELED:
                listener.onCanceled();//取消下载
                break;
            case TYPE_FAILED:
                listener.onFailed();//下载失败
                break;
            case TYPE_PAUSED:
                listener.onPaused();//下载暂停
            default:
                break;
        }
    }

    public void pauseDownload(){
        isPaused = true;
    }

    public void cancelDownload(){
        isCanceled = true;
    }

    private long getContentLength(String downloadUrl) throws IOException{
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(downloadUrl)
                .build();
        Response response = client.newCall(request).execute();
        if (response != null && response.isSuccessful()){
            long contentLength = response.body().contentLength();
            response.body().close();
            return contentLength;
        }
        return 0;
    }

}
