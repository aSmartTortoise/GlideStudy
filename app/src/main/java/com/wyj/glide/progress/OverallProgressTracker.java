package com.wyj.glide.progress;

import android.util.Log;

public class OverallProgressTracker {
    private static final String TAG = "OverallProgressTracker";
    private int totalTasks = 2; // 总共有两个任务
    private int[] progress = new int[totalTasks];

    public OverallProgressTracker() {
    }

    public synchronized void updateProgress(int taskIndex, int taskProgress) {
        Log.d(TAG, "updateProgress: taskIndex:" + taskIndex + ", taskProgress:" + taskProgress);
        progress[taskIndex] = taskProgress;
        int overallProgress = calculateOverallProgress();
        Log.d(TAG, "updateProgress: overallProgress:" + overallProgress);
    }

    private int calculateOverallProgress() {
        int sum = 0;
        for (int p : progress) {
            sum += p;
        }
        return sum / totalTasks;
    }
}
