package com.ynu.marginx.application;

public interface ProgressListener {

    ProgressListener NOOP = new ProgressListener() {
        @Override
        public void started(int total) {
        }

        @Override
        public void advanced(int completed, int total) {
        }

        @Override
        public void finished() {
        }
    };

    void started(int total);

    void advanced(int completed, int total);

    void finished();
}
