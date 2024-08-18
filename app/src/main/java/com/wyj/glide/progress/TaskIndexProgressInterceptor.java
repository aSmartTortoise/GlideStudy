package com.wyj.glide.progress;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

public class TaskIndexProgressInterceptor implements Interceptor {
    private TaskIndexResponseProgressListener progressListener;

    private int taskIndex;

    public TaskIndexProgressInterceptor(TaskIndexResponseProgressListener progressListener, int taskIndex) {
        this.progressListener = progressListener;
        this.taskIndex = taskIndex;
    }

    public TaskIndexProgressInterceptor(TaskIndexResponseProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response originalResponse = chain.proceed(chain.request());
        return originalResponse.newBuilder()
                .body(new TaskIndexProgressResponseBody(originalResponse.body(), progressListener, taskIndex))
                .build();
    }
}