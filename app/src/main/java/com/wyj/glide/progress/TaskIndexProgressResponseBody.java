package com.wyj.glide.progress;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.*;

public class TaskIndexProgressResponseBody extends ResponseBody {

    private static final String TAG = "ProgressResponseBody";

    private final ResponseBody responseBody;
    private final TaskIndexResponseProgressListener progressListener;
    private BufferedSource bufferedSource;
    private int taskIndex;

    public TaskIndexProgressResponseBody(ResponseBody responseBody, TaskIndexResponseProgressListener progressListener) {
        this.responseBody = responseBody;
        this.progressListener = progressListener;
        Log.d(TAG, "ProgressResponseBody: content length:" + contentLength());
    }

    public TaskIndexProgressResponseBody(ResponseBody responseBody, TaskIndexResponseProgressListener progressListener, int taskIndex) {
        this.responseBody = responseBody;
        this.progressListener = progressListener;
        this.taskIndex = taskIndex;
    }

    @Override
    public MediaType contentType() {
        return responseBody.contentType();
    }

    @Override
    public long contentLength() {
        return responseBody.contentLength();
    }

    @Override
    public BufferedSource source() {
        if (bufferedSource == null) {
            bufferedSource = Okio.buffer(source(responseBody.source()));
        }
        return bufferedSource;
    }

    private Source source(Source source) {
        return new ForwardingSource(source) {
            long totalBytesRead = 0L;

            @Override
            public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                long bytesRead = super.read(sink, byteCount);
                Log.d(TAG, "read: taskIndex：" + taskIndex + " bytesRead:" + bytesRead
                        + " contentLength:" + contentLength());
                totalBytesRead += bytesRead != -1 ? bytesRead : 0;

                progressListener.update(taskIndex, totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                return bytesRead;
            }
        };
    }
}
