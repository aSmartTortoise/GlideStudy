package com.wyj.glide.progress;

public interface TaskIndexResponseProgressListener {
    void update(int taskIndex, long bytesRead, long contentLength, boolean done);
}
